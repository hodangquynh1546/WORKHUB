package com.workhub.realtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.workhub.access.AccessService;
import com.workhub.common.ApiException;
import com.workhub.document.CollabSessions;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The single WebSocket endpoint. A browser cannot set an Authorization header on a WebSocket, and putting the token
 * in the URL would leak it into logs, so the first frame must be {@code {"type":"auth","token":"<access token>"}};
 * a connection that has not authenticated within 10 seconds is closed. Afterwards the client may
 * {@code subscribe}/{@code unsubscribe} to projects it can see and {@code ping}.
 */
@Component
public class RealtimeSocketHandler extends TextWebSocketHandler {

	private static final long AUTH_TIMEOUT_SECONDS = 10;

	private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;

	private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor((r) -> {
		Thread t = new Thread(r, "ws-auth-timeout");
		t.setDaemon(true);
		return t;
	});

	private final RealtimeHub hub;

	private final JwtDecoder jwtDecoder;

	private final AccessService access;

	private final JsonMapper json;

	private final CollabSessions collab;

	RealtimeSocketHandler(RealtimeHub hub, JwtDecoder jwtDecoder, AccessService access, JsonMapper json, CollabSessions collab) {
		this.collab = collab;
		this.hub = hub;
		this.jwtDecoder = jwtDecoder;
		this.access = access;
		this.json = json;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		// Collaborative-editing frames carry base64 Yjs data; the default 8 KB limit would cut a pasted page.
		session.setTextMessageSizeLimit(MAX_FRAME_BYTES);
		timeouts.schedule(() -> {
			if (session.isOpen() && !(session.getAttributes().get(RealtimeHub.USER) instanceof UUID)) {
				close(session, CloseStatus.POLICY_VIOLATION.withReason("auth timeout"));
			}
		}, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		JsonNode frame;
		try {
			frame = json.readTree(message.getPayload());
		}
		catch (RuntimeException ex) {
			close(session, CloseStatus.BAD_DATA);
			return;
		}
		String type = frame.path("type").asString("");
		Object user = session.getAttributes().get(RealtimeHub.USER);
		if (!(user instanceof UUID userId)) {
			if (!"auth".equals(type)) {
				close(session, CloseStatus.POLICY_VIOLATION.withReason("auth first"));
				return;
			}
			authenticate(session, frame.path("token").asString(""));
			return;
		}
		switch (type) {
			case "ping" -> reply(session, Map.of("type", "pong"));
			case "subscribe", "unsubscribe" -> {
				UUID projectId = parseId(frame.path("projectId").asString(""));
				if (projectId == null) {
					return;
				}
				boolean on = "subscribe".equals(type);
				if (on && !canSee(userId, projectId)) {
					reply(session, Map.of("type", "error", "code", "PROJECT_NOT_FOUND", "projectId", projectId));
					return;
				}
				hub.subscribe(session, projectId, on);
				reply(session, Map.of("type", on ? "subscribed" : "unsubscribed", "projectId", projectId));
			}
			case "doc.join", "doc.leave", "doc.update", "doc.awareness" -> {
				UUID documentId = parseId(frame.path("documentId").asString(""));
				if (documentId != null) {
					switch (type) {
						case "doc.join" -> collab.join(session, userId, documentId);
						case "doc.leave" -> collab.leave(session, documentId);
						case "doc.update" -> collab.update(session, documentId, frame.path("epoch").asInt(0), frame.path("update").asString(""));
						default -> collab.awareness(session, documentId, frame.path("clientId").asLong(0), frame.path("update").asString(""));
					}
				}
			}
			default -> {
				// Unknown frames are ignored so newer clients can talk to older servers.
			}
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		collab.closed(session);
		hub.unregister(session);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		collab.closed(session);
		hub.unregister(session);
		close(session, CloseStatus.SERVER_ERROR);
	}

	private void authenticate(WebSocketSession session, String token) {
		try {
			Jwt jwt = jwtDecoder.decode(token);
			UUID userId = UUID.fromString(jwt.getSubject());
			WebSocketSession wrapped = hub.register(session, userId);
			// Replies go through the decorated session from now on.
			reply(wrapped, Map.of("type", "ready", "userId", userId));
		}
		catch (JwtException | IllegalArgumentException ex) {
			close(session, CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
		}
	}

	private boolean canSee(UUID userId, UUID projectId) {
		try {
			access.project(userId, projectId);
			return true;
		}
		catch (ApiException ex) {
			return false;
		}
	}

	private void reply(WebSocketSession session, Map<String, Object> payload) {
		try {
			// After authentication every frame goes through the decorated session so senders never interleave.
			Object wrapped = session.getAttributes().get(RealtimeHub.WRAPPED);
			((wrapped instanceof WebSocketSession w) ? w : session).sendMessage(new TextMessage(hub.serialize(payload)));
		}
		catch (Exception ex) {
			close(session, CloseStatus.SERVER_ERROR);
		}
	}

	private static void close(WebSocketSession session, CloseStatus status) {
		try {
			session.close(status);
		}
		catch (Exception ignored) {
			// already closed
		}
	}

	private static UUID parseId(String text) {
		try {
			return UUID.fromString(text);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
