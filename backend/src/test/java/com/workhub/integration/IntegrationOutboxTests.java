package com.workhub.integration;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IntegrationOutboxTests {

	record Delivery(String signature, String body) {
	}

	private static final List<Delivery> RECEIVED = new CopyOnWriteArrayList<>();

	private static final HttpServer SERVER = start();

	private static HttpServer start() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/hook", (exchange) -> {
				RECEIVED.add(new Delivery(exchange.getRequestHeaders().getFirst("X-WorkHub-Signature"),
						new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("workhub.integration.webhook-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/hook");
		registry.add("workhub.integration.webhook-secret", () -> "test-secret");
	}

	@AfterAll
	static void stop() {
		SERVER.stop(0);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private IntegrationOutbox outbox;

	@Autowired
	private JdbcTemplate jdbc;

	private String read(org.springframework.test.web.servlet.ResultActions r, String path) throws Exception {
		return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
	}

	@Test
	void changesToLinkedProjectsAreQueuedCoalescedAndDeliveredSigned() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Team");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		UUID plain = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());

		// Nothing is queued for a project without externally linked issues.
		mockMvc.perform(post("/api/projects/" + plain + "/issues").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TASK\",\"title\":\"local\"}")).andExpect(status().isCreated());
		assertThat(pending(plain)).isZero();

		String issue = read(mockMvc.perform(post("/api/projects/" + project + "/issues").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TASK\",\"title\":\"linked\",\"externalRef\":\"smartspec:usecase/x\"}"))
			.andExpect(status().isCreated()), "$.id");
		// Writes to a project with linked issues are queued; several of them coalesce into one notification.
		for (String s : new String[] { "IN_PROGRESS", "CODE_REVIEW", "DONE" }) {
			mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json("status", s))).andExpect(status().isOk());
		}
		assertThat(pending(project)).as("several changes coalesce into one pending notification").isEqualTo(1);

		RECEIVED.clear();
		assertThat(outbox.drain()).isGreaterThanOrEqualTo(1);
		Delivery d = RECEIVED.stream().filter((x) -> x.body().contains(project.toString())).findFirst().orElseThrow();
		assertThat(d.body()).contains("\"type\":\"project.changed\"");
		javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
		mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		assertThat(d.signature()).isEqualTo("sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(d.body().getBytes(StandardCharsets.UTF_8))));
		assertThat(pending(project)).isZero();
	}

	private int pending(UUID projectId) {
		Integer n = jdbc.queryForObject("select count(*) from integration_outbox where project_id = ? and delivered_at is null", Integer.class, projectId);
		return (n == null) ? 0 : n;
	}

}
