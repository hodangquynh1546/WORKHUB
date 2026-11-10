package com.workhub.workspace;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceMemberTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void permissionsReflectTheRole() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Perms");
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");

		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.permissions.length()").value(6));
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", admin.bearer()))
			.andExpect(jsonPath("$.myRole").value("ADMIN"))
			.andExpect(jsonPath("$.permissions[?(@ == 'MANAGE_ADMINS')]").isEmpty())
			.andExpect(jsonPath("$.permissions[?(@ == 'MANAGE_MEMBERS')]").isNotEmpty())
			.andExpect(jsonPath("$.permissions[?(@ == 'VIEW_AUDIT')]").isNotEmpty());
		mockMvc.perform(get("/api/workspaces").header("Authorization", member.bearer()))
			.andExpect(jsonPath("$[0].permissions").value(org.hamcrest.Matchers.contains("VIEW")));
	}

	@Test
	void onlyTheOwnerChangesRoles() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Roles");
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");

		changeRole(admin, ws, member, "ADMIN").andExpect(status().isForbidden());
		changeRole(owner, ws, member, "ADMIN").andExpect(status().isNoContent());
		changeRole(owner, ws, owner, "MEMBER").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("OWNER_PROTECTED"));
		changeRole(owner, ws, member, "OWNER").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ROLE"));
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", member.bearer()))
			.andExpect(jsonPath("$.myRole").value("ADMIN"));
	}

	@Test
	void adminsRemoveMembersButOnlyTheOwnerRemovesAdmins() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Removal");
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session otherAdmin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");

		remove(member, ws, admin).andExpect(status().isForbidden());
		remove(admin, ws, otherAdmin).andExpect(status().isForbidden());
		remove(admin, ws, owner).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("OWNER_PROTECTED"));
		remove(admin, ws, member).andExpect(status().isNoContent());
		remove(owner, ws, otherAdmin).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", member.bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/workspaces/" + ws + "/members").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void membersCanLeaveButTheOwnerCannot() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Leaving");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/teams/" + team + "/members")
			.header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"" + member.userId() + "\",\"role\":\"MEMBER\"}")).andExpect(status().isNoContent());

		remove(owner, ws, owner).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
		remove(member, ws, member).andExpect(status().isNoContent());

		// Leaving the workspace also leaves its teams.
		mockMvc.perform(get("/api/teams/" + team + "/members").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(1));
	}

	private ResultActions changeRole(Session caller, UUID ws, Session target, String role) throws Exception {
		return mockMvc.perform(put("/api/workspaces/" + ws + "/members/" + target.userId())
			.header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("role", role)));
	}

	private ResultActions remove(Session caller, UUID ws, Session target) throws Exception {
		return mockMvc.perform(
				delete("/api/workspaces/" + ws + "/members/" + target.userId()).header("Authorization", caller.bearer()));
	}

}
