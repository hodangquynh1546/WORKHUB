package com.workhub.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import com.workhub.auth.RefreshCookies;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Shared helpers for integration tests. Each test uses fresh random emails, so tests never collide. */
public final class TestApi {

	public static final String PASSWORD = "correct-horse-battery";

	private TestApi() {
	}

	public record Session(UUID userId, String email, String accessToken, Cookie refreshCookie) {

		public String bearer() {
			return "Bearer " + accessToken;
		}

	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	public static Session register(MockMvc mockMvc) throws Exception {
		return register(mockMvc, uniqueEmail());
	}

	public static Session register(MockMvc mockMvc, String email) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", PASSWORD, "fullName", "Test User")))
			.andExpect(status().isCreated())
			.andReturn();
		return session(result, email);
	}

	public static Session session(MvcResult result, String email) throws Exception {
		String body = result.getResponse().getContentAsString();
		return new Session(UUID.fromString(JsonPath.read(body, "$.user.id")), email,
				JsonPath.read(body, "$.accessToken"), result.getResponse().getCookie(RefreshCookies.NAME));
	}

	/** Builds a flat JSON object from key/value pairs; values are strings or null. */
	public static String json(String... keyValues) {
		StringBuilder json = new StringBuilder("{");
		for (int i = 0; i < keyValues.length; i += 2) {
			if (i > 0) {
				json.append(',');
			}
			json.append('"').append(keyValues[i]).append("\":");
			String value = keyValues[i + 1];
			json.append((value == null) ? "null" : '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"');
		}
		return json.append('}').toString();
	}

}
