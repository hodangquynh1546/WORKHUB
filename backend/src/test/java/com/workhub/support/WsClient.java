package com.workhub.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import com.workhub.support.TestApi.Session;

/** A WebSocket client for tests: records every text frame and close code; helpers wait for frames of a type. */
public final class WsClient implements WebSocket.Listener {

	private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

	private final BlockingQueue<Integer> closes = new LinkedBlockingQueue<>();

	private final StringBuilder partial = new StringBuilder();

	private WebSocket socket;

	public static WsClient connect(int port) throws Exception {
		WsClient client = new WsClient();
		client.socket = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
			.buildAsync(URI.create("ws://localhost:" + port + "/ws"), client).get(5, TimeUnit.SECONDS);
		return client;
	}

	public WsClient authenticate(Session session) throws Exception {
		send("{\"type\":\"auth\",\"token\":\"" + session.accessToken() + "\"}");
		assertThat(JsonPath.<String>read(next(), "$.type")).isEqualTo("ready");
		return this;
	}

	public void send(String json) throws Exception {
		socket.sendText(json, true).get(5, TimeUnit.SECONDS);
	}

	public String next() throws Exception {
		String frame = frames.poll(5, TimeUnit.SECONDS);
		assertThat(frame).as("a frame within 5 seconds").isNotNull();
		return frame;
	}

	/** The next frame of this type, skipping others. */
	public String next(String type) throws Exception {
		for (int i = 0; i < 30; i++) {
			String frame = next();
			if (type.equals(JsonPath.<String>read(frame, "$.type"))) {
				return frame;
			}
		}
		throw new AssertionError("No " + type + " frame");
	}

	public void expectSilence() throws Exception {
		assertThat(frames.poll(600, TimeUnit.MILLISECONDS)).isNull();
	}

	/** Assert that no frame of this type arrives within a short time (other frames are ignored). */
	public void expectNo(String type) throws Exception {
		long end = System.currentTimeMillis() + 600;
		while (System.currentTimeMillis() < end) {
			String frame = frames.poll(100, TimeUnit.MILLISECONDS);
			if (frame != null) {
				assertThat(JsonPath.<String>read(frame, "$.type")).isNotEqualTo(type);
			}
		}
	}

	public int closeCode() throws Exception {
		Integer code = closes.poll(5, TimeUnit.SECONDS);
		assertThat(code).as("connection closed").isNotNull();
		return code;
	}

	public void close() {
		socket.abort();
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		partial.append(data);
		if (last) {
			frames.add(partial.toString());
			partial.setLength(0);
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		closes.add(statusCode);
		return null;
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		webSocket.request(1);
	}

}
