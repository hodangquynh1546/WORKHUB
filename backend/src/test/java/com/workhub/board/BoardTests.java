package com.workhub.board;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class BoardTests {

	/** The five workflow entries as JSON; TO_DO/IN_PROGRESS names and the enabled flags vary per test. */
	private static String workflow(String todoName, String inProgressName, boolean todoOn, boolean reviewOn,
			boolean testingOn) {
		return "[{\"status\":\"TO_DO\",\"name\":\"" + todoName + "\",\"enabled\":" + todoOn + "},"
				+ "{\"status\":\"IN_PROGRESS\",\"name\":\"" + inProgressName + "\",\"enabled\":true},"
				+ "{\"status\":\"CODE_REVIEW\",\"name\":\"Review\",\"enabled\":" + reviewOn + "},"
				+ "{\"status\":\"TESTING\",\"name\":\"QA\",\"enabled\":" + testingOn + "},"
				+ "{\"status\":\"DONE\",\"name\":\"Done\",\"enabled\":true}]";
	}

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, Session po, Session dev) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session po = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, po, "PRODUCT_OWNER");
		Session dev = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, dev, "DEVELOPER");
		return new Fixture(owner, ws, team, project, po, dev);
	}

	@Test
	void scrumBoardShowsTheActiveSprintOnly() throws Exception {
		Fixture f = fixture();
		UUID inSprint = issue(f, "In sprint");
		issue(f, "Backlog only");

		mockMvc.perform(get("/api/projects/" + f.project() + "/board").header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.methodology").value("SCRUM"))
			.andExpect(jsonPath("$.sprint").doesNotExist())
			.andExpect(jsonPath("$.issues.length()").value(0))
			.andExpect(jsonPath("$.columns.length()").value(5))
			.andExpect(jsonPath("$.columns[0].name").value("Cần làm"));

		LocalDate start = LocalDate.now();
		UUID sprint = UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints")
			.header("Authorization", f.po().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"S1\",\"goal\":\"Ship it\",\"startDate\":\"" + start + "\",\"endDate\":\"" + start.plusDays(13) + "\"}"))
			.andReturn().getResponse().getContentAsString(), "$.id"));
		mockMvc.perform(post("/api/sprints/" + sprint + "/issues").header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"issueIds\":[\"" + inSprint + "\"],\"includeChildren\":false}"));
		mockMvc.perform(post("/api/sprints/" + sprint + "/start").header("Authorization", f.po().bearer()));

		mockMvc.perform(get("/api/projects/" + f.project() + "/board").header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.sprint.name").value("S1"))
			.andExpect(jsonPath("$.sprint.goal").value("Ship it"))
			.andExpect(jsonPath("$.issues.length()").value(1))
			.andExpect(jsonPath("$.issues[0].title").value("In sprint"))
			.andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("EDIT_ISSUES")));
	}

	@Test
	void kanbanBoardShowsOpenWorkWithoutEpics() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(put("/api/projects/" + f.project()).header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("name", "K", "methodology", "KANBAN")))
			.andExpect(status().isOk());
		UUID task = issue(f, "Open task");
		UUID finished = issue(f, "Just finished");
		mockMvc.perform(put("/api/issues/" + finished + "/status").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("status", "DONE")));
		mockMvc.perform(post("/api/projects/" + f.project() + "/issues").header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("type", "EPIC", "title", "An epic")));

		mockMvc.perform(get("/api/projects/" + f.project() + "/board").header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.methodology").value("KANBAN"))
			.andExpect(jsonPath("$.sprint").doesNotExist())
			.andExpect(jsonPath("$.issues.length()").value(2))
			.andExpect(jsonPath("$.issues[?(@.type == 'EPIC')]").isEmpty());
		org.assertj.core.api.Assertions.assertThat(task).isNotNull();
	}

	@Test
	void workflowColumnsCanBeRenamedAndOptionalOnesHidden() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(get("/api/projects/" + f.project() + "/workflow").header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.length()").value(5))
			.andExpect(jsonPath("$[2].status").value("CODE_REVIEW"))
			.andExpect(jsonPath("$[2].enabled").value(true));

		saveWorkflow(f.dev(), f, workflow("Backlog", "Doing", true, true, true)).andExpect(status().isForbidden());
		saveWorkflow(f.owner(), f, workflow("Mới", "Doing", true, false, true)).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Mới"))
			.andExpect(jsonPath("$[2].enabled").value(false));
		mockMvc.perform(get("/api/projects/" + f.project() + "/board").header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.columns.length()").value(4))
			.andExpect(jsonPath("$.columns[0].name").value("Mới"))
			.andExpect(jsonPath("$.columns[?(@.status == 'CODE_REVIEW')]").isEmpty());

		UUID task = issue(f, "Task");
		mockMvc.perform(put("/api/issues/" + task + "/status").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("status", "CODE_REVIEW")))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STATUS_DISABLED"));
		mockMvc.perform(put("/api/issues/" + task + "/status").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("status", "TESTING"))).andExpect(status().isOk());
	}

	@Test
	void workflowRulesAreEnforced() throws Exception {
		Fixture f = fixture();
		UUID task = issue(f, "Task");
		mockMvc.perform(put("/api/issues/" + task + "/status").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("status", "TESTING")));

		saveWorkflow(f.owner(), f, workflow("Mới", "Doing", true, true, false)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STATUS_IN_USE"));
		saveWorkflow(f.owner(), f, workflow("Mới", "Doing", false, true, true)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_WORKFLOW"));
		saveWorkflow(f.owner(), f, workflow("doing", "Doing", true, true, true)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DUPLICATE_STATUS_NAME"));
		saveWorkflow(f.owner(), f, "[{\"status\":\"TO_DO\",\"name\":\"Only one\",\"enabled\":true}]")
			.andExpect(status().isBadRequest());
	}

	private ResultActions saveWorkflow(Session caller, Fixture f, String body) throws Exception {
		return mockMvc.perform(put("/api/projects/" + f.project() + "/workflow").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private UUID issue(Fixture f, String title) throws Exception {
		return UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", f.po().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(json("type", "TASK", "title", title))).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id"));
	}

}
