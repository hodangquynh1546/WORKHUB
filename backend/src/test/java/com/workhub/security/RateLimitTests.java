package com.workhub.security;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "workhub.rate-limit.auth-requests=3")
@AutoConfigureMockMvc
class RateLimitTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void signInIsLimitedPerClientAndTheOtherEndpointsKeepTheirOwnCount() throws Exception {
		String body = json("email", "nobody-" + System.nanoTime() + "@example.com", "password", "wrong-password");
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));

		// Another address is not affected, and neither is another endpoint of the same address.
		mockMvc.perform(post("/api/auth/login").with((request) -> {
			request.setRemoteAddr("10.9.8.7");
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
			.content(json("email", "nobody@example.com"))).andExpect(status().is2xxSuccessful());
	}

	@Test
	void refreshIsNotLimited() throws Exception {
		for (int i = 0; i < 6; i++) {
			mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
		}
	}

}
