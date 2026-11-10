package com.workhub.document;

import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.workhub.common.ApiException;
import com.workhub.document.CollabService.Join;
import com.workhub.document.CollabService.ResetEvent;
import com.workhub.realtime.RealtimeHub;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;

/**
 * Who has a document open for collaborative editing, and relaying between them. Pure relay: updates are stored by
 * {@link CollabService} (so a late joiner can catch up) and then forwarded unchanged to the other members of the
 * room; awareness (cursors, names) is only forwarded and remembered for people who join later. One server instance
 * holds the rooms in memory, which is what the MVP deploys.
 *
 * <p>An empty document that already has text needs somebody to turn the text into CRDT data exactly once. The first
 * editor to join gets that job ("init"); if they leave before doing it, the next editor is asked.
 */
@Component
public class CollabSessions {

	private record Member(UUID userId, boolean canEdit, WebSocketSession session, Set<Long> clientIds, String[] lastAwareness) {
	}

	private final Map<UUID, Map<String, Member>> rooms = new ConcurrentHashMap<>();

	/** documentId -> session id that must seed the CRDT. */
	private final Map<UUID, String> initLeases = new ConcurrentHashMap<>();

	private final CollabService collab;

	private final RealtimeHub hub;

	CollabSessions(CollabService collab, RealtimeHub hub) {
		this.collab = collab;
		this.hub = hub;
	}

	public void join(WebSocketSession session, UUID userId, UUID documentId) {
		Join join;
		try {
			join = collab.join(userId, documentId);
		}
		catch (ApiException ex) {
			error(session, documentId, ex.getCode());
			return;
		}
		Map<String, Member> room = rooms.computeIfAbsent(documentId, (id) -> new ConcurrentHashMap<>());
		room.put(session.getId(), new Member(userId, join.canEdit(), session, ConcurrentHashMap.newKeySet(), new String[1]));
		boolean init = false;
		if (join.needsInit() && initLeases.putIfAbsent(documentId, session.getId()) == null) {
			init = true;
		}
		else if (join.needsInit()) {
			init = session.getId().equals(initLeases.get(documentId));
		}
		Map<String, Object> joined = new LinkedHashMap<>();
		joined.put("type", "doc.joined");
		joined.put("documentId", documentId);
		joined.put("epoch", join.epoch());
		joined.put("canEdit", join.canEdit());
		joined.put("init", init);
		hub.sendTo(session, joined);
		// Cursors and names of the people already there.
		for (Map.Entry<String, Member> peer : room.entrySet()) {
			String awareness = peer.getValue().lastAwareness()[0];
			if (!peer.getKey().equals(session.getId()) && awareness != null) {
				hub.sendTo(session, Map.of("type", "doc.awareness", "documentId", documentId, "update", awareness));
			}
		}
	}

	public void update(WebSocketSession session, UUID documentId, int epoch, String base64) {
		Member member = member(session, documentId);
		if (member == null || !member.canEdit()) {
			error(session, documentId, "FORBIDDEN");
			return;
		}
		long seq;
		try {
			seq = collab.append(member.userId(), documentId, epoch, Base64.getDecoder().decode(base64));
		}
		catch (ApiException ex) {
			if ("STALE_EPOCH".equals(ex.getCode())) {
				hub.sendTo(session, Map.of("type", "doc.reset", "documentId", documentId));
			}
			else {
				error(session, documentId, ex.getCode());
			}
			return;
		}
		catch (IllegalArgumentException ex) {
			error(session, documentId, "INVALID_DATA");
			return;
		}
		// Whoever sent the first update has done the seeding.
		initLeases.remove(documentId, session.getId());
		hub.sendTo(session, Map.of("type", "doc.ack", "documentId", documentId, "seq", seq));
		relay(documentId, session, Map.of("type", "doc.update", "documentId", documentId, "epoch", epoch, "seq", seq, "update", base64));
	}

	public void awareness(WebSocketSession session, UUID documentId, long clientId, String base64) {
		Member member = member(session, documentId);
		if (member == null) {
			return;
		}
		member.clientIds().add(clientId);
		member.lastAwareness()[0] = base64;
		relay(documentId, session, Map.of("type", "doc.awareness", "documentId", documentId, "update", base64));
	}

	public void leave(WebSocketSession session, UUID documentId) {
		Map<String, Member> room = rooms.get(documentId);
		if (room == null) {
			return;
		}
		Member left = room.remove(session.getId());
		if (left == null) {
			return;
		}
		if (!left.clientIds().isEmpty()) {
			relay(documentId, session, Map.of("type", "doc.peer_left", "documentId", documentId, "clientIds", new HashSet<>(left.clientIds())));
		}
		if (room.isEmpty()) {
			rooms.remove(documentId);
			initLeases.remove(documentId);
		}
		else if (session.getId().equals(initLeases.get(documentId))) {
			// The person who should have seeded the document left first: ask someone else who can edit.
			initLeases.remove(documentId);
			room.entrySet().stream().filter((e) -> e.getValue().canEdit()).findFirst().ifPresent((e) -> {
				initLeases.put(documentId, e.getKey());
				hub.sendTo(e.getValue().session(), Map.of("type", "doc.init", "documentId", documentId));
			});
		}
	}

	/** The connection went away: leave every room it was in. */
	public void closed(WebSocketSession session) {
		for (UUID documentId : new HashSet<>(rooms.keySet())) {
			leave(session, documentId);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onReset(ResetEvent event) {
		initLeases.remove(event.documentId());
		Map<String, Member> room = rooms.get(event.documentId());
		if (room != null) {
			room.values().forEach((m) -> hub.sendTo(m.session(), Map.of("type", "doc.reset", "documentId", event.documentId(), "epoch", event.epoch())));
		}
	}

	private Member member(WebSocketSession session, UUID documentId) {
		Map<String, Member> room = rooms.get(documentId);
		return (room == null) ? null : room.get(session.getId());
	}

	private void relay(UUID documentId, WebSocketSession from, Map<String, Object> frame) {
		Map<String, Member> room = rooms.get(documentId);
		if (room != null) {
			room.forEach((id, m) -> {
				if (!id.equals(from.getId())) {
					hub.sendTo(m.session(), frame);
				}
			});
		}
	}

	private void error(WebSocketSession session, UUID documentId, String code) {
		hub.sendTo(session, Map.of("type", "doc.error", "documentId", documentId, "code", code));
	}

}
