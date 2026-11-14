package com.workhub.integration;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	private String read(org.springframework.test.web.servlet.ResultActions r, String path) throws Exception {
		return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
	}

	@Test
	void registerThroughConnectThenExchangeTokenAndCreateLinkedIssue() throws Exception {
		String email = TestApi.uniqueEmail();
		String token = read(mockMvc.perform(post("/api/integrations/connect").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + TestApi.PASSWORD + "\",\"register\":true,\"fullName\":\"Bridge User\"}")
		).andExpect(status().isCreated()), "$.token");
		assertThat(token).startsWith("whk_");

		// Signing in again (not registering) with the same credentials also works and yields another token.
		mockMvc.perform(post("/api/integrations/connect").contentType(MediaType.APPLICATION_JSON)
			.content(json("email", email, "password", TestApi.PASSWORD))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/integrations/connect").contentType(MediaType.APPLICATION_JSON)
			.content(json("email", email, "password", "wrong-password-xx"))).andExpect(status().isUnauthorized());

		String access = read(mockMvc.perform(post("/api/integrations/token").contentType(MediaType.APPLICATION_JSON)
			.content(json("token", token))).andExpect(status().isOk()), "$.accessToken");
		String bearer = "Bearer " + access;

		String ws = read(mockMvc.perform(post("/api/workspaces").header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON).content(json("name", "Forge"))).andExpect(status().isCreated()), "$.id");
		String team = read(mockMvc.perform(post("/api/workspaces/" + ws + "/teams").header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON).content(json("name", "Shop"))).andExpect(status().isCreated()), "$.id");
		String project = read(mockMvc.perform(post("/api/teams/" + team + "/projects").header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"key\":\"" + TestApi.uniqueKey() + "\",\"name\":\"Shop\",\"methodology\":\"SCRUM\"}"))
			.andExpect(status().isCreated()), "$.id");

		String ref = "smartspec:usecase/" + UUID.randomUUID();
		String issue = read(mockMvc.perform(post("/api/projects/" + project + "/issues").header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"USER_STORY\",\"title\":\"Login\",\"externalRef\":\"" + ref + "\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.externalRef").value(ref)), "$.id");

		mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", bearer)
			.contentType(MediaType.APPLICATION_JSON).content(json("status", "IN_PROGRESS"))).andExpect(status().isOk());
		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", bearer)).andExpect(status().isOk())
			.andExpect(jsonPath("$.issue.externalRef").value(ref));
	}

	@Test
	void invalidOrForgedTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/integrations/token").contentType(MediaType.APPLICATION_JSON)
			.content(json("token", "whk_nope"))).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/integrations/tokens")).andExpect(status().isUnauthorized());
	}

}
