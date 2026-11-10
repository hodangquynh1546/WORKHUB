package com.workhub.realtime;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.databind.json.JsonMapper;

/**
 * Who is connected, and delivery of events to them. A user may have several sessions (tabs, devices). Besides
 * per-user delivery, a session can subscribe to a project (after the handler checked its access) to hear about
 * changes made to it. Delivery is best-effort: a slow or broken session is dropped, clients reconnect and reload.
 */
@Component
public class RealtimeHub {

	private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

	static final String USER = "userId";

	static final String PROJECTS = "projects";

	static final String WRAPPED = "wrapped";

	private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

	private final JsonMapper json;

	RealtimeHub(JsonMapper json) {
		this.json = json;
	}

	/** Wraps the raw session so concurrent senders cannot interleave frames, and remembers who it belongs to. */
	WebSocketSession register(WebSocketSession raw, UUID userId) {
		WebSocketSession session = (raw instanceof ConcurrentWebSocketSessionDecorator) ? raw
				: new ConcurrentWebSocketSessionDecorator(raw, 5_000, 256 * 1024);
		raw.getAttributes().put(USER, userId);
		raw.getAttributes().put(WRAPPED, session);
		raw.getAttributes().putIfAbsent(PROJECTS, ConcurrentHashMap.newKeySet());
		sessions.computeIfAbsent(userId, (id) -> new CopyOnWriteArraySet<>()).add(session);
		return session;
	}

	void unregister(WebSocketSession raw) {
		Object user = raw.getAttributes().get(USER);
		if (user instanceof UUID id) {
			Set<WebSocketSession> set = sessions.get(id);
			if (set != null) {
				set.removeIf((s) -> s.getId().equals(raw.getId()));
				if (set.isEmpty()) {
					sessions.remove(id);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	void subscribe(WebSocketSession session, UUID projectId, boolean on) {
		Set<UUID> projects = (Set<UUID>) session.getAttributes().computeIfAbsent(PROJECTS, (k) -> ConcurrentHashMap.newKeySet());
		if (on) {
			projects.add(projectId);
		}
		else {
			projects.remove(projectId);
		}
	}

	public void sendToUser(UUID userId, Object event) {
		Set<WebSocketSession> set = sessions.get(userId);
		if (set != null) {
			String text = serialize(event);
			set.forEach((s) -> send(s, text));
		}
	}

	public void sendToUsers(Collection<UUID> userIds, Object event) {
		String text = null;
		for (UUID id : new HashSet<>(userIds)) {
			Set<WebSocketSession> set = sessions.get(id);
			if (set != null) {
				text = (text == null) ? serialize(event) : text;
				for (WebSocketSession s : set) {
					send(s, text);
				}
			}
		}
	}

	/** Everyone subscribed to the project. */
	@SuppressWarnings("unchecked")
	public void sendToProject(UUID projectId, Object event) {
		String text = null;
		for (Set<WebSocketSession> set : sessions.values()) {
			for (WebSocketSession s : set) {
				Set<UUID> projects = (Set<UUID>) s.getAttributes().get(PROJECTS);
				if (projects != null && projects.contains(projectId)) {
					text = (text == null) ? serialize(event) : text;
					send(s, text);
				}
			}
		}
	}

	/** Which of these people have at least one open session. */
	public Set<UUID> online(Collection<UUID> userIds) {
		Set<UUID> out = new HashSet<>();
		for (UUID id : userIds) {
			Set<WebSocketSession> set = sessions.get(id);
			if (set != null && !set.isEmpty()) {
				out.add(id);
			}
		}
		return out;
	}

	public String serialize(Object event) {
		return json.writeValueAsString(event);
	}

	private void send(WebSocketSession session, String text) {
		try {
			if (session.isOpen()) {
				session.sendMessage(new TextMessage(text));
			}
		}
		catch (IOException | RuntimeException ex) {
			log.debug("Dropping websocket session {}: {}", session.getId(), ex.toString());
			try {
				session.close();
			}
			catch (IOException ignored) {
				// already gone
			}
		}
	}

}
