package com.workhub.project;

import static com.workhub.support.TestApi.json;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
class ProjectTests {

	@Autowired
	private MockMvc mockMvc;

	/** Owner, a team, and one extra workspace member (outside the team). */
	private record Fixture(Session owner, UUID workspace, UUID team, Session member) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		return new Fixture(owner, ws, team, TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER"));
	}

	@Test
	void creatorBecomesProjectManagerAndKeyIsUpperCased() throws Exception {
		Fixture f = fixture();

		mockMvc.perform(post("/api/teams/" + f.team() + "/projects").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("key", "ecom", "name", "  Shop  ", "description", "Online shop", "methodology", "SCRUM")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.key").value("ECOM"))
			.andExpect(jsonPath("$.name").value("Shop"))
			.andExpect(jsonPath("$.status").value("PLANNING"))
			.andExpect(jsonPath("$.methodology").value("SCRUM"))
			.andExpect(jsonPath("$.myRole").value("PROJECT_MANAGER"))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.teamName").value("Backend"));
	}

	@Test
	void keysAreUniquePerWorkspaceAndValidated() throws Exception {
		Fixture f = fixture();
		String key = TestApi.uniqueKey();
		TestApi.createProject(mockMvc, f.owner(), f.team(), key);
		UUID otherTeam = TestApi.createTeam(mockMvc, f.owner(), f.workspace(), "Frontend");

		create(f.owner(), otherTeam, key.toLowerCase(), "SCRUM").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_KEY_TAKEN"));
		create(f.owner(), f.team(), "1AB", "SCRUM").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.key").exists());
		create(f.owner(), f.team(), "A", "SCRUM").andExpect(status().isBadRequest());
		create(f.owner(), f.team(), "TOOLONGKEY99", "SCRUM").andExpect(status().isBadRequest());
		// Another workspace may reuse the key.
		Session other = TestApi.register(mockMvc);
		UUID otherWs = TestApi.createWorkspace(mockMvc, other, "Other");
		UUID team = TestApi.createTeam(mockMvc, other, otherWs, "T");
		create(other, team, key, "KANBAN").andExpect(status().isCreated());
	}

	@Test
	void datesMustBeInOrder() throws Exception {
		Fixture f = fixture();

		mockMvc.perform(post("/api/teams/" + f.team() + "/projects").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("key", TestApi.uniqueKey(), "name", "X", "methodology", "SCRUM", "startDate", "2026-12-01",
					"endDate", "2026-11-01")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATES"));
	}

	@Test
	void onlyTeamLeadsAndWorkspaceAdminsCreateProjects() throws Exception {
		Fixture f = fixture();
		Session teamMember = f.member();
		TestApi.addToTeam(mockMvc, f.owner(), f.team(), teamMember, "MEMBER");
		Session lead = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		TestApi.addToTeam(mockMvc, f.owner(), f.team(), lead, "LEAD");

		create(teamMember, f.team(), TestApi.uniqueKey(), "SCRUM").andExpect(status().isForbidden());
		create(lead, f.team(), TestApi.uniqueKey(), "SCRUM").andExpect(status().isCreated());
		Session outsider = TestApi.register(mockMvc);
		create(outsider, f.team(), TestApi.uniqueKey(), "SCRUM").andExpect(status().isNotFound());
	}

	@Test
	void visibilityFollowsTheTeam() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		Session teamMember = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		TestApi.addToTeam(mockMvc, f.owner(), f.team(), teamMember, "MEMBER");
		Session projectMember = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		TestApi.addToProject(mockMvc, f.owner(), project, projectMember, "DEVELOPER");

		// Team members can view; a workspace member who is neither on the team nor the project cannot.
		mockMvc.perform(get("/api/projects/" + project).header("Authorization", teamMember.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").doesNotExist())
			.andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.contains("VIEW")));
		mockMvc.perform(get("/api/projects/" + project).header("Authorization", projectMember.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("DEVELOPER"));
		mockMvc.perform(get("/api/projects/" + project).header("Authorization", f.member().bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

		mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/projects").header("Authorization", f.member().bearer()))
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/projects").header("Authorization", teamMember.bearer()))
			.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/projects").header("Authorization", projectMember.bearer()))
			.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/teams/" + f.team() + "/projects").header("Authorization", f.owner().bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].memberCount").value(2));
	}

	@Test
	void permissionsFollowTheProjectRole() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		Session po = member(f, project, "PRODUCT_OWNER");
		Session dev = member(f, project, "DEVELOPER");
		Session stakeholder = member(f, project, "STAKEHOLDER");

		permissions(f.owner(), project).andExpect(jsonPath("$.permissions.length()").value(7));
		permissions(po, project).andExpect(jsonPath("$.permissions", containsInAnyOrder("VIEW", "MANAGE_BACKLOG", "MANAGE_SPRINTS",
				"EDIT_ISSUES", "COMMENT")));
		permissions(dev, project).andExpect(jsonPath("$.permissions", containsInAnyOrder("VIEW", "EDIT_ISSUES", "COMMENT")));
		permissions(stakeholder, project).andExpect(jsonPath("$.permissions", containsInAnyOrder("VIEW", "COMMENT")));

		for (Session s : new Session[] { po, dev, stakeholder }) {
			update(s, project).andExpect(status().isForbidden());
			setStatus(s, project, "ACTIVE").andExpect(status().isForbidden());
			addMember(s, project, f.member().userId(), "DEVELOPER").andExpect(status().isForbidden());
		}
	}

	@Test
	void managersUpdateTheProjectButNotItsKey() throws Exception {
		Fixture f = fixture();
		String key = TestApi.uniqueKey();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), key);
		Session pm = member(f, project, "PROJECT_MANAGER");

		mockMvc.perform(put("/api/projects/" + project).header("Authorization", pm.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("key", "HACKED", "name", "Renamed", "description", " ", "methodology", "KANBAN",
					"startDate", "2026-01-05", "endDate", "2026-06-30")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.key").value(key))
			.andExpect(jsonPath("$.name").value("Renamed"))
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.methodology").value("KANBAN"))
			.andExpect(jsonPath("$.startDate").value("2026-01-05"));
		setStatus(pm, project, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void archivedProjectsAreReadOnlyUntilTheStatusChanges() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());

		setStatus(f.owner(), project, "ARCHIVED").andExpect(jsonPath("$.status").value("ARCHIVED"));
		update(f.owner(), project).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		addMember(f.owner(), project, f.member().userId(), "DEVELOPER").andExpect(status().isConflict());
		mockMvc.perform(get("/api/projects/" + project).header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk());

		setStatus(f.owner(), project, "ACTIVE").andExpect(jsonPath("$.status").value("ACTIVE"));
		update(f.owner(), project).andExpect(status().isOk());
	}

	@Test
	void productOwnerAndScrumMasterAreHeldByOneMemberEach() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		Session po = member(f, project, "PRODUCT_OWNER");
		Session other = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		addMember(f.owner(), project, other.userId(), "PRODUCT_OWNER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ROLE_TAKEN"));
		addMember(f.owner(), project, other.userId(), "DEVELOPER").andExpect(status().isNoContent());
		changeRole(f.owner(), project, other, "PRODUCT_OWNER").andExpect(status().isConflict());
		changeRole(f.owner(), project, po, "SCRUM_MASTER").andExpect(status().isNoContent());
		changeRole(f.owner(), project, other, "PRODUCT_OWNER").andExpect(status().isNoContent());
		addMember(f.owner(), project, other.userId(), "DEVELOPER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
	}

	@Test
	void aProjectKeepsAtLeastOneProjectManager() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		Session second = member(f, project, "PROJECT_MANAGER");

		// The owner created the project, so they are the first manager.
		changeRole(second, project, f.owner(), "DEVELOPER").andExpect(status().isNoContent());
		remove(second, project, second).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LAST_PROJECT_MANAGER"));
		changeRole(f.owner(), project, second, "DEVELOPER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LAST_PROJECT_MANAGER"));
	}

	@Test
	void membersMustBeInTheWorkspaceAndCanLeave() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		Session outsider = TestApi.register(mockMvc);
		Session dev = member(f, project, "DEVELOPER");

		addMember(f.owner(), project, outsider.userId(), "DEVELOPER").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
		remove(dev, project, f.owner()).andExpect(status().isForbidden());
		remove(dev, project, dev).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/projects/" + project + "/members").header("Authorization", f.owner().bearer()))
			.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void teamLeadsAndAdminsManageProjectsWithoutBeingMembers() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		Session lead = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		TestApi.addToTeam(mockMvc, f.owner(), f.team(), lead, "LEAD");
		Session admin = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "ADMIN");

		for (Session s : new Session[] { lead, admin }) {
			permissions(s, project).andExpect(jsonPath("$.permissions.length()").value(7));
			update(s, project).andExpect(status().isOk());
		}
		addMember(lead, project, f.member().userId(), "QA_ENGINEER").andExpect(status().isNoContent());
	}

	@Test
	void leavingTheWorkspaceRemovesProjectMemberships() throws Exception {
		Fixture f = fixture();
		UUID project = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		Session dev = member(f, project, "DEVELOPER");

		mockMvc.perform(delete("/api/workspaces/" + f.workspace() + "/members/" + dev.userId())
			.header("Authorization", f.owner().bearer())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/projects/" + project + "/members").header("Authorization", f.owner().bearer()))
			.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/projects/" + project).header("Authorization", dev.bearer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void initialMembersAreAddedAtCreation() throws Exception {
		Fixture f = fixture();
		Session other = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		mockMvc.perform(post("/api/teams/" + f.team() + "/projects").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"key\":\"" + TestApi.uniqueKey() + "\",\"name\":\"With members\",\"methodology\":\"SCRUM\","
					+ "\"members\":[{\"userId\":\"" + f.member().userId() + "\",\"role\":\"PRODUCT_OWNER\"},"
					+ "{\"userId\":\"" + other.userId() + "\",\"role\":\"SCRUM_MASTER\"}]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.memberCount").value(3));
		mockMvc.perform(post("/api/teams/" + f.team() + "/projects").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"key\":\"" + TestApi.uniqueKey() + "\",\"name\":\"Two POs\",\"methodology\":\"SCRUM\","
					+ "\"members\":[{\"userId\":\"" + f.member().userId() + "\",\"role\":\"PRODUCT_OWNER\"},"
					+ "{\"userId\":\"" + other.userId() + "\",\"role\":\"PRODUCT_OWNER\"}]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ROLE_TAKEN"));
	}

	// ---- helpers ----

	/** A new workspace member added to the project with {@code role}. */
	private Session member(Fixture f, UUID project, String role) throws Exception {
		Session s = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		TestApi.addToProject(mockMvc, f.owner(), project, s, role);
		return s;
	}

	private ResultActions create(Session caller, UUID team, String key, String methodology) throws Exception {
		return mockMvc.perform(post("/api/teams/" + team + "/projects").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("key", key, "name", "Name", "methodology", methodology)));
	}

	private ResultActions update(Session caller, UUID project) throws Exception {
		return mockMvc.perform(put("/api/projects/" + project).header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "Updated", "methodology", "SCRUM")));
	}

	private ResultActions setStatus(Session caller, UUID project, String status) throws Exception {
		return mockMvc.perform(put("/api/projects/" + project + "/status").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("status", status)));
	}

	/** GET of the project, asserted to succeed; callers match against {@code $.permissions}. */
	private ResultActions permissions(Session caller, UUID project) throws Exception {
		return mockMvc.perform(get("/api/projects/" + project).header("Authorization", caller.bearer()))
			.andExpect(status().isOk());
	}

	private ResultActions addMember(Session caller, UUID project, UUID userId, String role) throws Exception {
		return mockMvc.perform(post("/api/projects/" + project + "/members").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("userId", userId.toString(), "role", role)));
	}

	private ResultActions changeRole(Session caller, UUID project, Session target, String role) throws Exception {
		return mockMvc.perform(put("/api/projects/" + project + "/members/" + target.userId())
			.header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("role", role)));
	}

	private ResultActions remove(Session caller, UUID project, Session target) throws Exception {
		return mockMvc.perform(delete("/api/projects/" + project + "/members/" + target.userId())
			.header("Authorization", caller.bearer()));
	}

}
