package com.workhub.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Test
	void protectedEndpointRejectsMissingToken() throws Exception {
		mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void protectedEndpointAcceptsIssuedToken() throws Exception {
		TestApi.Session session = TestApi.register(mockMvc);
		String token = jwtService.issueAccessToken(session.userId(), session.email());

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(session.userId().toString()))
			.andExpect(jsonPath("$.email").value(session.email()));
	}

	@Test
	void protectedEndpointRejectsTokenSignedWithAnotherKey() throws Exception {
		String otherSecret = "another-secret-that-is-at-least-32-bytes-long";
		var otherKey = new SecretKeySpec(otherSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		var forger = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)),
				new JwtProperties("workhub", otherSecret, Duration.ofMinutes(15)));
		String forged = forger.issueAccessToken(UUID.randomUUID(), "attacker@example.com");

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + forged))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void healthEndpointIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
