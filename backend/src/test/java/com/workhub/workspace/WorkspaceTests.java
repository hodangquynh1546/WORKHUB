package com.workhub.workspace;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkspaceMemberRepository members;

	@Test
	void creatorBecomesOwnerAndSeesWorkspace() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID id = create(owner, "Acme Corp");

		mockMvc.perform(get("/api/workspaces").header("Authorization", owner.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(id.toString()))
			.andExpect(jsonPath("$[0].myRole").value("OWNER"));
		mockMvc.perform(get("/api/workspaces/" + id + "/members").header("Authorization", owner.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].userId").value(owner.userId().toString()))
			.andExpect(jsonPath("$[0].role").value("OWNER"));
	}

	@Test
	void ownerCanUpdateSettings() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID id = create(owner, "Old name");

		mockMvc.perform(put("/api/workspaces/" + id).header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "New name", "description", "  ", "logoUrl", null)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("New name"))
			.andExpect(jsonPath("$.description").doesNotExist());
	}

	@Test
	void nonMembersCannotSeeOrChangeAWorkspace() throws Exception {
		Session owner = TestApi.register(mockMvc);
		Session stranger = TestApi.register(mockMvc);
		UUID id = create(owner, "Private");

		mockMvc.perform(get("/api/workspaces/" + id).header("Authorization", stranger.bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
		mockMvc.perform(get("/api/workspaces/" + id + "/members").header("Authorization", stranger.bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/workspaces/" + id).header("Authorization", stranger.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "Hijacked")))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/workspaces").header("Authorization", stranger.bearer()))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void plainMembersCanViewButNotChangeSettings() throws Exception {
		Session owner = TestApi.register(mockMvc);
		Session member = TestApi.register(mockMvc);
		UUID id = create(owner, "Team space");
		// Invitations arrive in M2; add the membership directly for now.
		members.save(new WorkspaceMember(id, member.userId(), WorkspaceRole.MEMBER));

		mockMvc.perform(get("/api/workspaces/" + id).header("Authorization", member.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("MEMBER"));
		mockMvc.perform(put("/api/workspaces/" + id).header("Authorization", member.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "Renamed")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void createValidatesInput() throws Exception {
		Session owner = TestApi.register(mockMvc);

		mockMvc.perform(post("/api/workspaces").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", " ", "logoUrl", "ftp://example.com/logo.png")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.logoUrl").exists());
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/workspaces")).andExpect(status().isUnauthorized());
	}

	private UUID create(Session session, String name) throws Exception {
		String body = mockMvc
			.perform(post("/api/workspaces").header("Authorization", session.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("name", name, "description", "Test workspace")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.myRole").value("OWNER"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

}
