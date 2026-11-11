package com.workhub.workspace;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class DeletionTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	/** Fills a project with one of everything that hangs off it, so a cascade that misses a table fails here. */
	private void fill(Session owner, UUID project) throws Exception {
		mockMvc.perform(post("/api/projects/" + project + "/issues").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("type", "TASK", "title", "Something to do")))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/projects/" + project + "/documents").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("title", "Notes", "type", "OTHER", "content", "hello")))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/chat/conversations").header("Authorization", owner.bearer())).andExpect(status().isOk());
	}

	private ResultActions deleteWith(String path, Session caller, String name) throws Exception {
		return mockMvc.perform(delete(path).header("Authorization", caller.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(json("name", name)));
	}

	private int count(String table, String column, UUID id) {
		return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, id);
	}

	@Test
	void ownerDeletesAWorkspaceWithEverythingInIt() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Doomed");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Core");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		fill(owner, project);
		assertThat(count("issues", "project_id", project)).isEqualTo(1);

		deleteWith("/api/workspaces/" + ws, owner, "wrong name").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NAME_MISMATCH"));
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", owner.bearer())).andExpect(status().isOk());

		deleteWith("/api/workspaces/" + ws, owner, "Doomed").andExpect(status().isNoContent());

		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", owner.bearer())).andExpect(status().isNotFound());
		assertThat(count("projects", "workspace_id", ws)).isZero();
		assertThat(count("teams", "workspace_id", ws)).isZero();
		assertThat(count("issues", "project_id", project)).isZero();
		assertThat(count("documents", "project_id", project)).isZero();
		assertThat(count("workspace_members", "workspace_id", ws)).isZero();

		// The deletion stays in the actor's own trail, with the name taken from the confirmation.
		mockMvc.perform(get("/api/me/audit-log").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.items[?(@.action == 'workspace.delete')].detail").value(org.hamcrest.Matchers.contains("name=Doomed")));
	}

	@Test
	void aBusyWorkspaceWithChatSprintsAndMeetingsDeletesCleanly() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Busy");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Core");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session dev = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, dev, "DEVELOPER");
		String auth = owner.bearer();

		String story = mockMvc.perform(post("/api/projects/" + project + "/issues").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TASK\",\"title\":\"Parent\",\"storyPoints\":3}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String storyId = com.jayway.jsonpath.JsonPath.read(story, "$.id");
		mockMvc.perform(post("/api/projects/" + project + "/issues").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON).content(json("type", "SUB_TASK", "title", "Child", "parentId", storyId,
					"assigneeId", dev.userId().toString())))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/issues/" + storyId + "/comments").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON).content(json("body", "A comment")))
			.andExpect(status().isCreated());
		java.time.LocalDate start = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
		String sprint = mockMvc.perform(post("/api/projects/" + project + "/sprints").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"S1\",\"startDate\":\"" + start + "\",\"endDate\":\"" + start.plusDays(6) + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String sprintId = com.jayway.jsonpath.JsonPath.read(sprint, "$.id");
		mockMvc.perform(post("/api/sprints/" + sprintId + "/issues").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON).content("{\"issueIds\":[\"" + storyId + "\"],\"includeChildren\":true}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/sprints/" + sprintId + "/start").header("Authorization", auth)).andExpect(status().isOk());
		mockMvc.perform(put("/api/issues/" + storyId + "/status").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON).content(json("status", "DONE"))).andExpect(status().isOk());

		String channel = mockMvc.perform(post("/api/chat/projects/" + project).header("Authorization", auth))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String conversation = com.jayway.jsonpath.JsonPath.read(channel, "$.id");
		String first = mockMvc.perform(post("/api/chat/conversations/" + conversation + "/messages").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\",\"mentionIds\":[\"" + dev.userId() + "\"]}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String firstId = com.jayway.jsonpath.JsonPath.read(first, "$.id");
		mockMvc.perform(post("/api/chat/conversations/" + conversation + "/messages").header("Authorization", dev.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"reply\",\"replyToId\":\"" + firstId + "\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/chat/messages/" + firstId + "/reactions").header("Authorization", dev.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("emoji", "👍"))).andExpect(status().isOk());
		mockMvc.perform(post("/api/chat/conversations/" + conversation + "/read").header("Authorization", dev.bearer())).andExpect(status().isOk());
		mockMvc.perform(post("/api/chat/direct").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
			.content(json("workspaceId", ws.toString(), "userId", dev.userId().toString()))).andExpect(status().isOk());

		java.time.Instant at = java.time.Instant.now().plusSeconds(86400);
		mockMvc.perform(post("/api/projects/" + project + "/meetings").header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Sync\",\"type\":\"GENERAL\",\"startsAt\":\"" + at + "\",\"endsAt\":\"" + at.plusSeconds(1800)
					+ "\",\"attendeeIds\":[\"" + dev.userId() + "\"]}"))
			.andExpect(status().isCreated());

		deleteWith("/api/workspaces/" + ws, owner, "Busy").andExpect(status().isNoContent());
		assertThat(count("conversations", "workspace_id", ws)).isZero();
		assertThat(count("projects", "workspace_id", ws)).isZero();
	}

	@Test
	void onlyTheOwnerMayDeleteAWorkspace() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Safe");
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session outsider = TestApi.register(mockMvc);

		deleteWith("/api/workspaces/" + ws, admin, "Safe").andExpect(status().isForbidden());
		deleteWith("/api/workspaces/" + ws, outsider, "Safe").andExpect(status().isNotFound());
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", owner.bearer())).andExpect(status().isOk());
	}

	@Test
	void teamLeadDeletesATeamAndItsProjectsButNotTheRestOfTheWorkspace() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Keeps");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Going");
		UUID other = TestApi.createTeam(mockMvc, owner, ws, "Staying");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		fill(owner, project);
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");

		deleteWith("/api/teams/" + team, member, "Going").andExpect(status().isNotFound());
		deleteWith("/api/teams/" + team, owner, "Nope").andExpect(status().isBadRequest());
		deleteWith("/api/teams/" + team, owner, "Going").andExpect(status().isNoContent());

		mockMvc.perform(get("/api/teams/" + team).header("Authorization", owner.bearer())).andExpect(status().isNotFound());
		assertThat(count("projects", "team_id", team)).isZero();
		assertThat(count("issues", "project_id", project)).isZero();
		mockMvc.perform(get("/api/teams/" + other).header("Authorization", owner.bearer())).andExpect(status().isOk());
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", owner.bearer())).andExpect(status().isOk());
	}

}
