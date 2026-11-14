package com.workhub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** One account for WorkHub and SmartSpec: the peer (SmartSpec, through ForgeBridge) is a stub here. */
@SpringBootTest
@AutoConfigureMockMvc
class IdentityTests {

	/** email -> peer answer as "status|fullName|emailVerified" (the stub looks the e-mail up). */
	private static final Map<String, String> PEER = new ConcurrentHashMap<>();

	private static final HttpServer SERVER = start();

	private static HttpServer start() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/identity", (exchange) -> {
				String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				Matcher m = Pattern.compile("\"email\":\"([^\"]+)\"").matcher(body);
				String answer = m.find() ? PEER.getOrDefault(m.group(1), "not_found||false") : "not_found||false";
				String[] parts = answer.split("\\|", -1);
				String changed = (parts.length > 3 && !parts[3].isEmpty()) ? "\"" + parts[3] + "\"" : "null";
				String json = "{\"status\":\"" + parts[0] + "\",\"fullName\":" + (parts[1].isEmpty() ? "null" : "\"" + parts[1] + "\"")
						+ ",\"emailVerified\":" + parts[2] + ",\"passwordChangedAt\":" + changed + "}";
				byte[] out = json.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, out.length);
				exchange.getResponseBody().write(out);
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
		registry.add("workhub.integration.identity-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/identity");
		registry.add("workhub.integration.webhook-secret", () -> "id-secret");
	}

	@AfterAll
	static void stop() {
		SERVER.stop(0);
	}

	@Autowired
	private MockMvc mockMvc;

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	@Test
	void anAccountCreatedInSmartSpecSignsInHereAndIsAdopted() throws Exception {
		String email = TestApi.uniqueEmail();
		PEER.put(email, "ok|An Nguyen|true");
		String token = JsonPath.read(login(email, "correct-horse-battery").andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.accessToken");
		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("An Nguyen")).andExpect(jsonPath("$.emailVerified").value(true));

		// adopted for good: it works even when the peer no longer answers ok
		PEER.put(email, "unavailable||false");
		login(email, "correct-horse-battery").andExpect(status().isOk());
		login(email, "another-password-1").andExpect(status().isUnauthorized());
	}

	@Test
	void aPasswordChangedInSmartSpecIsPickedUpHere() throws Exception {
		String email = TestApi.uniqueEmail();
		TestApi.register(mockMvc, email); // password correct-horse-battery
		login(email, "brand-new-password").andExpect(status().isUnauthorized());
		// the peer's password is NEWER than ours: adopted
		PEER.put(email, "ok|Someone|true|" + java.time.Instant.now().plusSeconds(3600));
		login(email, "brand-new-password").andExpect(status().isOk());
		PEER.remove(email);
		login(email, "brand-new-password").andExpect(status().isOk()); // now the local hash is the new one
		login(email, TestApi.PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void aStalePasswordInTheOtherSystemIsNeverAdopted() throws Exception {
		String email = TestApi.uniqueEmail();
		TestApi.register(mockMvc, email);
		// the peer still has an OLDER password (changed before ours): its "ok" must not overwrite ours
		PEER.put(email, "ok|Old|true|" + java.time.Instant.now().minusSeconds(86400));
		login(email, "an-old-password-1").andExpect(status().isUnauthorized());
		login(email, TestApi.PASSWORD).andExpect(status().isOk());
		// an answer without a timestamp is treated as unknown, not as newer
		PEER.put(email, "ok|Old|true");
		login(email, "an-old-password-1").andExpect(status().isUnauthorized());
	}

	@Test
	void theOtherSystemCanSetThePasswordAndOldSessionsAreRevoked() throws Exception {
		String email = TestApi.uniqueEmail();
		var session = TestApi.register(mockMvc, email);
		String body = "{\"email\":\"" + email + "\",\"password\":\"set-from-smartspec\"}";
		mockMvc.perform(post("/api/integrations/identity/set-password").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/integrations/identity/set-password").header("X-Integration-Key", "id-secret")
			.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
		login(email, "set-from-smartspec").andExpect(status().isOk());
		login(email, TestApi.PASSWORD).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie())).andExpect(status().isUnauthorized());
	}

	@Test
	void signUpIsRefusedWhenTheAddressExistsInSmartSpecWithAnotherPassword() throws Exception {
		String email = TestApi.uniqueEmail();
		PEER.put(email, "wrong_password||false");
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\",\"fullName\":\"X\"}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void signUpWithTheSameCredentialsAsSmartSpecIsVerifiedAlready() throws Exception {
		String email = TestApi.uniqueEmail();
		PEER.put(email, "ok|Same Person|true");
		String token = JsonPath.read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\",\"fullName\":\"Same Person\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.accessToken");
		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.emailVerified").value(true));
	}

	@Test
	void unknownEverywhereOrPeerDownBehavesAsBefore() throws Exception {
		String email = TestApi.uniqueEmail();
		login(email, "correct-horse-battery").andExpect(status().isUnauthorized());
		PEER.put(email, "unavailable||false");
		login(email, "correct-horse-battery").andExpect(status().isUnauthorized());
	}

	@Test
	void theCredentialCheckForTheOtherSystemNeedsTheIntegrationKey() throws Exception {
		String email = TestApi.uniqueEmail();
		TestApi.register(mockMvc, email);
		String body = "{\"email\":\"" + email + "\",\"password\":\"" + TestApi.PASSWORD + "\"}";
		mockMvc.perform(post("/api/integrations/identity/verify").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/integrations/identity/verify").header("X-Integration-Key", "id-secret")
			.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
		mockMvc.perform(post("/api/integrations/identity/verify").header("X-Integration-Key", "id-secret")
			.contentType(MediaType.APPLICATION_JSON).content(body.replace(TestApi.PASSWORD, "nope-nope-nope")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("wrong_password"));
		mockMvc.perform(post("/api/integrations/identity/verify").header("X-Integration-Key", "id-secret")
			.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody-" + email + "\",\"password\":\"x\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("not_found"));
		assertThat(email).isNotBlank();
	}

}
