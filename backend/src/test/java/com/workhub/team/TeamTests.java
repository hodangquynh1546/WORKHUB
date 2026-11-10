package com.workhub.team;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class TeamTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void adminsCreateTeamsAndBecomeLead() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");

		UUID team = TestApi.createTeam(mockMvc, admin, ws, "Backend Team");
		mockMvc.perform(get("/api/teams/" + team).header("Authorization", admin.bearer()))
			.andExpect(jsonPath("$.myRole").value("LEAD"))
			.andExpect(jsonPath("$.memberCount").value(1));

		mockMvc.perform(post("/api/workspaces/" + ws + "/teams").header("Authorization", member.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "Rogue"))).andExpect(status().isForbidden());

		// Every workspace member can see the teams, read-only.
		mockMvc.perform(get("/api/workspaces/" + ws + "/teams").header("Authorization", member.bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].myRole").doesNotExist())
			.andExpect(jsonPath("$[0].permissions").value(org.hamcrest.Matchers.contains("VIEW")));
	}

	@Test
	void teamNamesAreUniquePerWorkspaceIgnoringCase() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		TestApi.createTeam(mockMvc, owner, ws, "QA Team");

		mockMvc.perform(post("/api/workspaces/" + ws + "/teams").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "qa team")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TEAM_NAME_TAKEN"));
	}

	@Test
	void leadsManageTheirOwnTeamOnly() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		Session lead = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		Session dev = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		UUID frontend = TestApi.createTeam(mockMvc, owner, ws, "Frontend");
		UUID mobile = TestApi.createTeam(mockMvc, owner, ws, "Mobile");
		addMember(owner, frontend, lead.userId(), "LEAD").andExpect(status().isNoContent());

		addMember(lead, frontend, dev.userId(), "MEMBER").andExpect(status().isNoContent());
		addMember(lead, frontend, dev.userId(), "MEMBER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
		addMember(lead, mobile, dev.userId(), "MEMBER").andExpect(status().isForbidden());
		addMember(dev, frontend, owner.userId(), "MEMBER").andExpect(status().isForbidden());

		mockMvc.perform(put("/api/teams/" + frontend + "/members/" + dev.userId()).header("Authorization", lead.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("role", "LEAD"))).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/teams/" + frontend).header("Authorization", dev.bearer()))
			.andExpect(jsonPath("$.myRole").value("LEAD"));
	}

	@Test
	void onlyWorkspaceMembersCanBeAddedDirectly() throws Exception {
		Session owner = TestApi.register(mockMvc);
		Session outsider = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Design");

		addMember(owner, team, outsider.userId(), "MEMBER").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
		mockMvc.perform(get("/api/teams/" + team).header("Authorization", outsider.bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
	}

	@Test
	void archivedTeamsAreReadOnlyUntilUnarchived() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Legacy");

		mockMvc.perform(post("/api/teams/" + team + "/archive").header("Authorization", member.bearer()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/teams/" + team + "/archive").header("Authorization", owner.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archived").value(true));
		addMember(owner, team, member.userId(), "MEMBER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TEAM_ARCHIVED"));
		mockMvc.perform(get("/api/workspaces/" + ws + "/teams").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/workspaces/" + ws + "/teams?archived=true").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(post("/api/teams/" + team + "/unarchive").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.archived").value(false));
		addMember(owner, team, member.userId(), "MEMBER").andExpect(status().isNoContent());
	}

	@Test
	void membersCanLeaveATeam() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Ops");
		addMember(owner, team, member.userId(), "MEMBER");

		mockMvc.perform(delete("/api/teams/" + team + "/members/" + owner.userId()).header("Authorization", member.bearer()))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/teams/" + team + "/members/" + member.userId()).header("Authorization", member.bearer()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/teams/" + team + "/members").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(1));
	}

	private ResultActions addMember(Session caller, UUID team, UUID userId, String role) throws Exception {
		return mockMvc.perform(post("/api/teams/" + team + "/members").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("userId", userId.toString(), "role", role)));
	}

}
