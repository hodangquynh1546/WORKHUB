package com.workhub.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Real WebSocket connections against a running server (random port); the REST calls go through MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RealtimeTests {

	@LocalServerPort
	private int port;

	@Autowired
	private MockMvc mockMvc;

	/** A connected client that records every text frame and how the connection ended. */
	private final class Client implements WebSocket.Listener {

		final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

		final BlockingQueue<Integer> closes = new LinkedBlockingQueue<>();

		WebSocket socket;

		private final StringBuilder partial = new StringBuilder();

		Client connect() throws Exception {
			socket = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
				.buildAsync(URI.create("ws://localhost:" + port + "/ws"), this).get(5, TimeUnit.SECONDS);
			return this;
		}

		Client authenticate(Session session) throws Exception {
			send("{\"type\":\"auth\",\"token\":\"" + session.accessToken() + "\"}");
			assertThat(JsonPath.<String>read(next(), "$.type")).isEqualTo("ready");
			return this;
		}

		void send(String json) throws Exception {
			socket.sendText(json, true).get(5, TimeUnit.SECONDS);
		}

		String next() throws Exception {
			String frame = frames.poll(5, TimeUnit.SECONDS);
			assertThat(frame).as("a frame within 5 seconds").isNotNull();
			return frame;
		}

		/** The next frame of this type, skipping others. */
		String next(String type) throws Exception {
			for (int i = 0; i < 20; i++) {
				String frame = next();
				if (type.equals(JsonPath.<String>read(frame, "$.type"))) {
					return frame;
				}
			}
			throw new AssertionError("No " + type + " frame");
		}

		void expectSilence() throws Exception {
			assertThat(frames.poll(700, TimeUnit.MILLISECONDS)).isNull();
		}

		int closeCode() throws Exception {
			Integer code = closes.poll(5, TimeUnit.SECONDS);
			assertThat(code).as("connection closed").isNotNull();
			return code;
		}

		void close() {
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

	private record Fixture(Session owner, UUID project, Session dev) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session dev = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToTeam(mockMvc, owner, team, dev, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, dev, "DEVELOPER");
		return new Fixture(owner, project, dev);
	}

	// ---- connecting ----

	@Test
	void aSocketMustAuthenticateWithItsFirstFrame() throws Exception {
		Client noAuth = new Client().connect();
		noAuth.send("{\"type\":\"subscribe\",\"projectId\":\"" + UUID.randomUUID() + "\"}");
		assertThat(noAuth.closeCode()).isEqualTo(1008);

		Client badToken = new Client().connect();
		badToken.send("{\"type\":\"auth\",\"token\":\"not-a-jwt\"}");
		assertThat(badToken.closeCode()).isEqualTo(1008);

		Client garbage = new Client().connect();
		garbage.send("this is not json");
		assertThat(garbage.closeCode()).isEqualTo(1007);

		Session session = TestApi.register(mockMvc);
		Client good = new Client().connect().authenticate(session);
		good.send("{\"type\":\"ping\"}");
		assertThat(JsonPath.<String>read(good.next(), "$.type")).isEqualTo("pong");
		good.close();
	}

	@Test
	void presenceShowsWhoHasAnOpenSocket() throws Exception {
		Session online = TestApi.register(mockMvc);
		Session offline = TestApi.register(mockMvc);
		Client client = new Client().connect().authenticate(online);

		mockMvc.perform(get("/api/presence").param("userIds", online.userId() + "," + offline.userId())
			.header("Authorization", online.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.online.length()").value(1))
			.andExpect(jsonPath("$.online[0]").value(online.userId().toString()));
		client.close();
	}

	// ---- project changes ----

	@Test
	void subscribersHearAboutChangesToTheirProject() throws Exception {
		Fixture f = fixture();
		Session outsider = TestApi.register(mockMvc);

		Client watcher = new Client().connect().authenticate(f.dev());
		watcher.send("{\"type\":\"subscribe\",\"projectId\":\"" + f.project() + "\"}");
		assertThat(JsonPath.<String>read(watcher.next(), "$.type")).isEqualTo("subscribed");
		// Subscribing to a project you cannot see is refused.
		Client intruder = new Client().connect().authenticate(outsider);
		intruder.send("{\"type\":\"subscribe\",\"projectId\":\"" + f.project() + "\"}");
		String refused = intruder.next();
		assertThat(JsonPath.<String>read(refused, "$.type")).isEqualTo("error");
		assertThat(JsonPath.<String>read(refused, "$.code")).isEqualTo("PROJECT_NOT_FOUND");

		String issue = JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"Realtime\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		String event = watcher.next("project.changed");
		assertThat(JsonPath.<String>read(event, "$.projectId")).isEqualTo(f.project().toString());
		assertThat(JsonPath.<String>read(event, "$.area")).isEqualTo("issues");
		assertThat(JsonPath.<String>read(event, "$.actorId")).isEqualTo(f.owner().userId().toString());
		// A change addressed by the record's own id still reaches the project's subscribers.
		mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
		assertThat(JsonPath.<String>read(watcher.next("project.changed"), "$.area")).isEqualTo("issues");
		// Reads and failed writes say nothing, and the event carries no content.
		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", f.owner().bearer())).andExpect(status().isOk());
		mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"NOPE\"}")).andExpect(status().isBadRequest());
		watcher.expectSilence();
		intruder.expectSilence();

		watcher.send("{\"type\":\"unsubscribe\",\"projectId\":\"" + f.project() + "\"}");
		assertThat(JsonPath.<String>read(watcher.next("unsubscribed"), "$.type")).isEqualTo("unsubscribed");
		mockMvc.perform(post("/api/projects/" + f.project() + "/issues").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TASK\",\"title\":\"Quiet\"}")).andExpect(status().isCreated());
		watcher.expectSilence();
		watcher.close();
		intruder.close();
	}

	// ---- personal events ----

	@Test
	void notificationsAndChatMessagesArePushedToTheRecipient() throws Exception {
		Fixture f = fixture();
		Client dev = new Client().connect().authenticate(f.dev());
		Client owner = new Client().connect().authenticate(f.owner());

		String issue = JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"Push me\"}")).andReturn().getResponse().getContentAsString(), "$.id");
		mockMvc.perform(put("/api/issues/" + issue + "/assignee").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + f.dev().userId() + "\"}")).andExpect(status().isOk());

		String pushed = dev.next("notification");
		assertThat(JsonPath.<String>read(pushed, "$.notification.type")).isEqualTo("ISSUE_ASSIGNED");
		assertThat(JsonPath.<Integer>read(pushed, "$.unread")).isGreaterThanOrEqualTo(1);
		// The person who made the change is not told about it.
		for (int i = 0; i < 5; i++) {
			String frame = owner.frames.poll(300, TimeUnit.MILLISECONDS);
			if (frame != null) {
				assertThat(JsonPath.<String>read(frame, "$.type")).isNotEqualTo("notification");
			}
		}

		List<String> channels = JsonPath.read(mockMvc.perform(get("/api/chat/conversations").header("Authorization", f.owner().bearer()))
			.andReturn().getResponse().getContentAsString(), "$[?(@.kind=='PROJECT')].id");
		mockMvc.perform(post("/api/chat/conversations/" + channels.get(0) + "/messages").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hello live\"}")).andExpect(status().isCreated());
		String message = dev.next("chat.message");
		assertThat(JsonPath.<String>read(message, "$.message.body")).isEqualTo("hello live");
		assertThat(JsonPath.<String>read(message, "$.conversationId")).isEqualTo(channels.get(0));
		// The sender's other tabs get it too, so their view stays in step.
		assertThat(JsonPath.<String>read(owner.next("chat.message"), "$.message.sender.id")).isEqualTo(f.owner().userId().toString());
		dev.close();
		owner.close();
	}

}
