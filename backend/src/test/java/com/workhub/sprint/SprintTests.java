package com.workhub.sprint;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
class SprintTests {

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, Session po, Session sm,
			Session dev, Session stakeholder) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		return new Fixture(owner, ws, team, project, member(owner, ws, project, "PRODUCT_OWNER"),
				member(owner, ws, project, "SCRUM_MASTER"), member(owner, ws, project, "DEVELOPER"),
				member(owner, ws, project, "STAKEHOLDER"));
	}

	private Session member(Session owner, UUID ws, UUID project, String role) throws Exception {
		Session s = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, s, role);
		return s;
	}

	// ---- creating sprints ----

	@Test
	void productOwnersAndScrumMastersCreateSprints() throws Exception {
		Fixture f = fixture();
		LocalDate start = LocalDate.now();

		sprintBody(f.po(), f.project(), "Sprint 1", start, start.plusDays(13), f.sm().userId().toString(), 20)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FUTURE"))
			.andExpect(jsonPath("$.name").value("Sprint 1"))
			.andExpect(jsonPath("$.facilitator.id").value(f.sm().userId().toString()))
			.andExpect(jsonPath("$.capacityPoints").value(20))
			.andExpect(jsonPath("$.issueCount").value(0));
		createSprint(f.sm(), f.project(), "Sprint 2").andExpect(status().isCreated());
		createSprint(f.dev(), f.project(), "Nope").andExpect(status().isForbidden());
		createSprint(f.stakeholder(), f.project(), "Nope").andExpect(status().isForbidden());
		mockMvc.perform(get("/api/projects/" + f.project() + "/sprints").header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void sprintDatesAndFacilitatorAreValidated() throws Exception {
		Fixture f = fixture();
		LocalDate start = LocalDate.of(2026, 3, 1);
		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		sprintBody(f.po(), f.project(), "Backwards", start, start.minusDays(1), null, null)
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATES"));
		sprintBody(f.po(), f.project(), "Too long", start, start.plusMonths(1).plusDays(1), null, null)
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SPRINT_TOO_LONG"));
		sprintBody(f.po(), f.project(), "One month", start, start.plusMonths(1), null, null)
			.andExpect(status().isCreated());
		sprintBody(f.po(), f.project(), "Bad facilitator", start, start.plusDays(7), outsider.userId().toString(), null)
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FACILITATOR_NOT_MEMBER"));
		mockMvc.perform(post("/api/projects/" + f.project() + "/sprints").header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("name", " ")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void kanbanProjectsHaveNoSprints() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(put("/api/projects/" + f.project()).header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("name", "Kanban", "methodology", "KANBAN")))
			.andExpect(status().isOk());

		createSprint(f.po(), f.project(), "Sprint").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SPRINTS_NOT_SUPPORTED"));
	}

	@Test
	void switchingToKanbanIsRefusedWhileSprintsAreOpen() throws Exception {
		Fixture f = fixture();
		createSprint(f.po(), f.project(), "Sprint").andExpect(status().isCreated());

		mockMvc.perform(put("/api/projects/" + f.project()).header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("name", "X", "methodology", "KANBAN")))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPEN_SPRINTS"));
	}

	// ---- planning ----

	@Test
	void planningAddsIssuesAndCountsPoints() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint 1"));
		UUID a = issue(f, "TASK", "A", 5, null);
		UUID b = issue(f, "BUG", "B", 3, null);
		UUID epic = issue(f, "EPIC", "Epic", null, null);
		UUID done = issue(f, "TASK", "Done already", 2, null);
		setStatus(f.dev(), done, "DONE");

		addIssues(f.po(), sprint, false, a, b).andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.issueCount").value(2))
			.andExpect(jsonPath("$.sprint.scopePoints").value(8))
			.andExpect(jsonPath("$.dashboard.totalIssues").value(2))
			.andExpect(jsonPath("$.dashboard.scopePoints").value(8))
			.andExpect(jsonPath("$.issues.length()").value(2))
			.andExpect(jsonPath("$.issues[0].sprint.name").value("Sprint 1"));
		// Adding again changes nothing.
		addIssues(f.po(), sprint, false, a).andExpect(jsonPath("$.sprint.issueCount").value(2));
		addIssues(f.po(), sprint, false, epic).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EPIC_NOT_ALLOWED"));
		addIssues(f.po(), sprint, false, done).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ISSUE_DONE"));
		addIssues(f.po(), sprint, false, UUID.randomUUID()).andExpect(status().isBadRequest());
		addIssues(f.dev(), sprint, false, a).andExpect(status().isForbidden());

		UUID otherProject = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		UUID foreign = idOf(mockMvc.perform(post("/api/projects/" + otherProject + "/issues")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(json("type", "TASK", "title", "Elsewhere"))));
		addIssues(f.po(), sprint, false, foreign).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ISSUE"));
	}

	@Test
	void includeChildrenPullsInUnfinishedDescendants() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint 1"));
		UUID story = issue(f, "USER_STORY", "Story", 8, null);
		UUID one = issue(f, "SUB_TASK", "One", null, story);
		UUID two = issue(f, "SUB_TASK", "Two", null, story);
		setStatus(f.dev(), two, "DONE");

		addIssues(f.po(), sprint, true, story).andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.issueCount").value(2));
		mockMvc.perform(get("/api/issues/" + one).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.issue.sprint.id").value(sprint.toString()));
		mockMvc.perform(get("/api/issues/" + two).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.issue.sprint").doesNotExist());

		mockMvc.perform(delete("/api/sprints/" + sprint + "/issues/" + story + "?includeChildren=true")
			.header("Authorization", f.po().bearer())).andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.issueCount").value(0));
	}

	@Test
	void anIssueMovesBetweenFutureSprints() throws Exception {
		Fixture f = fixture();
		UUID first = id(createSprint(f.po(), f.project(), "First"));
		UUID second = id(createSprint(f.po(), f.project(), "Second"));
		UUID task = issue(f, "TASK", "Task", 3, null);
		addIssues(f.po(), first, false, task);

		addIssues(f.po(), second, false, task).andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.issueCount").value(1));
		mockMvc.perform(get("/api/sprints/" + first).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.sprint.issueCount").value(0));
		mockMvc.perform(delete("/api/sprints/" + first + "/issues/" + task).header("Authorization", f.po().bearer()))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ISSUE_NOT_IN_SPRINT"));
	}

	@Test
	void issuesInOpenSprintsCannotBeDeleted() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		UUID task = issue(f, "TASK", "Task", 3, null);
		addIssues(f.po(), sprint, false, task);

		mockMvc.perform(delete("/api/issues/" + task).header("Authorization", f.po().bearer()))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IN_SPRINT"));
		mockMvc.perform(delete("/api/sprints/" + sprint + "/issues/" + task).header("Authorization", f.po().bearer()))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/issues/" + task).header("Authorization", f.po().bearer()))
			.andExpect(status().isNoContent());
	}

	// ---- starting ----

	@Test
	void startingFreezesTheCommitmentAndNeedsIssues() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint 1"));
		UUID a = issue(f, "TASK", "A", 5, null);
		UUID b = issue(f, "TASK", "B", 3, null);

		act(f.sm(), "/api/sprints/" + sprint + "/start").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SPRINT_EMPTY"));
		addIssues(f.po(), sprint, false, a, b);
		act(f.dev(), "/api/sprints/" + sprint + "/start").andExpect(status().isForbidden());
		act(f.sm(), "/api/sprints/" + sprint + "/start").andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.status").value("ACTIVE"))
			.andExpect(jsonPath("$.sprint.committedPoints").value(8))
			.andExpect(jsonPath("$.sprint.committedIssues").value(2))
			.andExpect(jsonPath("$.sprint.startedAt").isNotEmpty())
			.andExpect(jsonPath("$.dashboard.committedPoints").value(8))
			.andExpect(jsonPath("$.dashboard.addedPoints").value(0));
		act(f.sm(), "/api/sprints/" + sprint + "/start").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SPRINT_NOT_FUTURE"));

		// The first snapshot is written at the start.
		mockMvc.perform(get("/api/sprints/" + sprint + "/snapshots").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].scopePoints").value(8))
			.andExpect(jsonPath("$[0].donePoints").value(0))
			.andExpect(jsonPath("$[0].todoIssues").value(2));
	}

	@Test
	void onlyOneSprintRunsAtATime() throws Exception {
		Fixture f = fixture();
		UUID one = id(createSprint(f.po(), f.project(), "One"));
		UUID two = id(createSprint(f.po(), f.project(), "Two"));
		addIssues(f.po(), one, false, issue(f, "TASK", "A", 1, null));
		addIssues(f.po(), two, false, issue(f, "TASK", "B", 1, null));

		act(f.po(), "/api/sprints/" + one + "/start").andExpect(status().isOk());
		act(f.po(), "/api/sprints/" + two + "/start").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SPRINT_ALREADY_ACTIVE"));
	}

	@Test
	void runningSprintsKeepTheirStartDate() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		addIssues(f.po(), sprint, false, issue(f, "TASK", "A", 1, null));
		LocalDate start = LocalDate.now();
		act(f.po(), "/api/sprints/" + sprint + "/start");

		updateSprint(f.po(), sprint, "Renamed", start.plusDays(1), start.plusDays(14)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("START_DATE_FIXED"));
		updateSprint(f.po(), sprint, "Renamed", start, start.plusDays(20)).andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Renamed"))
			.andExpect(jsonPath("$.endDate").value(start.plusDays(20).toString()));
	}

	// ---- scope changes and snapshots ----

	@Test
	void scopeChangesAfterTheStartAreTracked() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		UUID a = issue(f, "TASK", "A", 5, null);
		UUID b = issue(f, "TASK", "B", 3, null);
		UUID late = issue(f, "TASK", "Late", 2, null);
		addIssues(f.po(), sprint, false, a, b);
		act(f.po(), "/api/sprints/" + sprint + "/start");

		addIssues(f.po(), sprint, false, late).andExpect(status().isOk())
			.andExpect(jsonPath("$.dashboard.scopePoints").value(10))
			.andExpect(jsonPath("$.dashboard.committedPoints").value(8))
			.andExpect(jsonPath("$.dashboard.addedPoints").value(2))
			.andExpect(jsonPath("$.dashboard.addedIssues").value(1));
		mockMvc.perform(delete("/api/sprints/" + sprint + "/issues/" + b).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.dashboard.scopePoints").value(7))
			.andExpect(jsonPath("$.dashboard.removedPoints").value(3))
			.andExpect(jsonPath("$.dashboard.removedIssues").value(1));

		String body = mockMvc.perform(get("/api/sprints/" + sprint + "/snapshots").header("Authorization", f.po().bearer()))
			.andReturn().getResponse().getContentAsString();
		// All of this happened on one day, so there is still one row holding the last state.
		assertThat((List<?>) JsonPath.read(body, "$")).hasSize(1);
		assertThat(JsonPath.<Integer>read(body, "$[0].scopePoints")).isEqualTo(7);
		assertThat(JsonPath.<Integer>read(body, "$[0].scopeIssues")).isEqualTo(2);
	}

	@Test
	void statusAndPointChangesRefreshTheSnapshot() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		UUID a = issue(f, "TASK", "A", 5, null);
		UUID b = issue(f, "TASK", "B", 3, null);
		addIssues(f.po(), sprint, false, a, b);
		act(f.po(), "/api/sprints/" + sprint + "/start");

		setStatus(f.dev(), a, "DONE");
		setStatus(f.dev(), b, "IN_PROGRESS");
		snapshots(f, sprint).andExpect(jsonPath("$[0].donePoints").value(5))
			.andExpect(jsonPath("$[0].doneIssues").value(1))
			.andExpect(jsonPath("$[0].inProgressIssues").value(1))
			.andExpect(jsonPath("$[0].todoIssues").value(0));

		mockMvc.perform(put("/api/issues/" + b).header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"B\",\"priority\":\"MEDIUM\",\"refinement\":\"NEW\",\"storyPoints\":8}"))
			.andExpect(status().isOk());
		snapshots(f, sprint).andExpect(jsonPath("$[0].scopePoints").value(13));
		mockMvc.perform(get("/api/sprints/" + sprint).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.dashboard.remainingPoints").value(8))
			.andExpect(jsonPath("$.dashboard.doneIssues").value(1));
	}

	@Test
	void dashboardCountsBlockedAndOverdue() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		UUID a = issue(f, "TASK", "A", 5, null);
		UUID b = issue(f, "TASK", "B", 3, null);
		addIssues(f.po(), sprint, false, a, b);
		act(f.po(), "/api/sprints/" + sprint + "/start");
		mockMvc.perform(put("/api/issues/" + a + "/blocked").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("blocked", null)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/issues/" + a + "/blocked").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true,\"reason\":\"Waiting for API\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocked").value(true))
			.andExpect(jsonPath("$.blockedReason").value("Waiting for API"));
		mockMvc.perform(put("/api/issues/" + b).header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"B\",\"priority\":\"MEDIUM\",\"refinement\":\"NEW\",\"storyPoints\":3,\"dueDate\":\"2026-01-05\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/sprints/" + sprint + "?today=2026-02-01").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.dashboard.blockedIssues").value(1))
			.andExpect(jsonPath("$.dashboard.overdueIssues").value(1));
		mockMvc.perform(get("/api/sprints/" + sprint + "?today=2025-12-01").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.dashboard.overdueIssues").value(0));
		mockMvc.perform(put("/api/issues/" + a + "/blocked").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":false,\"reason\":\"ignored\"}"))
			.andExpect(jsonPath("$.blocked").value(false))
			.andExpect(jsonPath("$.blockedReason").doesNotExist());
	}

	// ---- completing ----

	@Test
	void completingRecordsTheOutcomeAndSendsUnfinishedWorkBack() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint 1"));
		UUID done = issue(f, "TASK", "Done", 5, null);
		UUID open = issue(f, "TASK", "Open", 3, null);
		UUID late = issue(f, "TASK", "Late", 2, null);
		addIssues(f.po(), sprint, false, done, open);
		act(f.po(), "/api/sprints/" + sprint + "/start");
		addIssues(f.po(), sprint, false, late);
		mockMvc.perform(delete("/api/sprints/" + sprint + "/issues/" + late).header("Authorization", f.po().bearer()));
		setStatus(f.dev(), done, "DONE");
		setStatus(f.dev(), open, "IN_PROGRESS");

		complete(f.dev(), sprint, "ACHIEVED", "BACKLOG", null).andExpect(status().isForbidden());
		complete(f.sm(), sprint, "PARTIALLY_ACHIEVED", "BACKLOG", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.status").value("COMPLETED"))
			.andExpect(jsonPath("$.sprint.completedPoints").value(5))
			.andExpect(jsonPath("$.sprint.completedIssues").value(1))
			.andExpect(jsonPath("$.sprint.committedPoints").value(8))
			.andExpect(jsonPath("$.sprint.goalOutcome").value("PARTIALLY_ACHIEVED"))
			.andExpect(jsonPath("$.issues.length()").value(1));
		// The unfinished issue is back in the backlog, status untouched; the finished one stays with the sprint.
		mockMvc.perform(get("/api/issues/" + open).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.issue.sprint").doesNotExist())
			.andExpect(jsonPath("$.issue.status").value("IN_PROGRESS"));
		mockMvc.perform(get("/api/issues/" + done).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.issue.sprint.status").value("COMPLETED"));

		mockMvc.perform(get("/api/sprints/" + sprint + "/report").header("Authorization", f.po().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.doneIssues.length()").value(1))
			.andExpect(jsonPath("$.doneIssues[0].title").value("Done"))
			.andExpect(jsonPath("$.unfinishedIssues.length()").value(1))
			.andExpect(jsonPath("$.unfinishedIssues[0].title").value("Open"))
			.andExpect(jsonPath("$.removedIssues.length()").value(1))
			.andExpect(jsonPath("$.removedIssues[0].title").value("Late"))
			.andExpect(jsonPath("$.dashboard.totalIssues").value(2))
			.andExpect(jsonPath("$.dashboard.doneIssues").value(1))
			.andExpect(jsonPath("$.dashboard.scopePoints").value(8))
			.andExpect(jsonPath("$.dashboard.donePoints").value(5))
			.andExpect(jsonPath("$.snapshots.length()").value(1));
		complete(f.sm(), sprint, "ACHIEVED", "BACKLOG", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SPRINT_NOT_ACTIVE"));
		// A completed sprint is frozen, and a new one can start.
		updateSprint(f.po(), sprint, "Changed", LocalDate.now(), LocalDate.now().plusDays(7)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SPRINT_CLOSED"));
		addIssues(f.po(), sprint, false, open).andExpect(status().isConflict());
	}

	@Test
	void unfinishedWorkCanGoStraightIntoTheNextSprint() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint 1"));
		UUID next = id(createSprint(f.po(), f.project(), "Sprint 2"));
		UUID open = issue(f, "TASK", "Open", 3, null);
		addIssues(f.po(), sprint, false, open);
		act(f.po(), "/api/sprints/" + sprint + "/start");

		complete(f.po(), sprint, "NOT_ACHIEVED", "NEXT_SPRINT", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_NEXT_SPRINT"));
		complete(f.po(), sprint, "NOT_ACHIEVED", "NEXT_SPRINT", sprint).andExpect(status().isBadRequest());
		complete(f.po(), sprint, "NOT_ACHIEVED", "NEXT_SPRINT", next).andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.completedIssues").value(0));

		mockMvc.perform(get("/api/sprints/" + next).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.sprint.issueCount").value(1))
			.andExpect(jsonPath("$.issues[0].title").value("Open"));
		// The carry-over is planning, not scope creep.
		act(f.po(), "/api/sprints/" + next + "/start").andExpect(status().isOk())
			.andExpect(jsonPath("$.dashboard.addedPoints").value(0))
			.andExpect(jsonPath("$.sprint.committedPoints").value(3));
	}

	@Test
	void cancellingReturnsEverythingToTheBacklog() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		UUID task = issue(f, "TASK", "Task", 3, null);
		addIssues(f.po(), sprint, false, task);
		act(f.po(), "/api/sprints/" + sprint + "/start");

		act(f.dev(), "/api/sprints/" + sprint + "/cancel").andExpect(status().isForbidden());
		act(f.po(), "/api/sprints/" + sprint + "/cancel").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		mockMvc.perform(get("/api/issues/" + task).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.issue.sprint").doesNotExist());
		act(f.po(), "/api/sprints/" + sprint + "/cancel").andExpect(status().isConflict());
		mockMvc.perform(get("/api/sprints/" + sprint + "/report").header("Authorization", f.po().bearer()))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SPRINT_NOT_COMPLETED"));
	}

	@Test
	void backlogCanBeFilteredBySprintMembership() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		UUID planned = issue(f, "TASK", "Planned", 3, null);
		issue(f, "TASK", "Unplanned", 3, null);
		addIssues(f.po(), sprint, false, planned);

		mockMvc.perform(get("/api/projects/" + f.project() + "/issues?sprintId=" + sprint).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].title").value("Planned"));
		mockMvc.perform(get("/api/projects/" + f.project() + "/issues?unplanned=true").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].title").value("Unplanned"));
	}

	@Test
	void outsidersCannotSeeSprints() throws Exception {
		Fixture f = fixture();
		UUID sprint = id(createSprint(f.po(), f.project(), "Sprint"));
		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		mockMvc.perform(get("/api/sprints/" + sprint).header("Authorization", outsider.bearer()))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));
		mockMvc.perform(get("/api/projects/" + f.project() + "/sprints").header("Authorization", outsider.bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/sprints/" + sprint).header("Authorization", f.stakeholder().bearer()))
			.andExpect(status().isOk());
	}

	// ---- helpers ----

	private ResultActions createSprint(Session caller, UUID project, String name) throws Exception {
		LocalDate start = LocalDate.now();
		return sprintBody(caller, project, name, start, start.plusDays(13), null, null);
	}

	private ResultActions sprintBody(Session caller, UUID project, String name, LocalDate start, LocalDate end,
			String facilitator, Integer capacity) throws Exception {
		return mockMvc.perform(post("/api/projects/" + project + "/sprints").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(sprintJson(name, start, end, facilitator, capacity)));
	}

	private ResultActions updateSprint(Session caller, UUID sprint, String name, LocalDate start, LocalDate end) throws Exception {
		return mockMvc.perform(put("/api/sprints/" + sprint).header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(sprintJson(name, start, end, null, null)));
	}

	private static String sprintJson(String name, LocalDate start, LocalDate end, String facilitator, Integer capacity) {
		return "{\"name\":\"" + name + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\""
				+ ((facilitator != null) ? ",\"facilitatorId\":\"" + facilitator + "\"" : "")
				+ ((capacity != null) ? ",\"capacityPoints\":" + capacity : "") + "}";
	}

	private ResultActions addIssues(Session caller, UUID sprint, boolean includeChildren, UUID... ids) throws Exception {
		String list = java.util.Arrays.stream(ids).map((id) -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
		return mockMvc.perform(post("/api/sprints/" + sprint + "/issues").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"issueIds\":[" + list + "],\"includeChildren\":" + includeChildren + "}"));
	}

	private ResultActions complete(Session caller, UUID sprint, String outcome, String to, UUID next) throws Exception {
		return mockMvc.perform(post("/api/sprints/" + sprint + "/complete").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"goalOutcome\":\"" + outcome + "\",\"goalNote\":\"note\",\"unfinishedTo\":\"" + to + "\""
					+ ((next != null) ? ",\"nextSprintId\":\"" + next + "\"" : "") + "}"));
	}

	private ResultActions act(Session caller, String path) throws Exception {
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
			.header("Authorization", caller.bearer()));
	}

	private ResultActions snapshots(Fixture f, UUID sprint) throws Exception {
		return mockMvc.perform(get("/api/sprints/" + sprint + "/snapshots").header("Authorization", f.po().bearer()))
			.andExpect(status().isOk());
	}

	private UUID issue(Fixture f, String type, String title, Integer points, UUID parent) throws Exception {
		StringBuilder body = new StringBuilder("{\"type\":\"" + type + "\",\"title\":\"" + title + "\"");
		if (points != null) {
			body.append(",\"storyPoints\":").append(points);
		}
		if (parent != null) {
			body.append(",\"parentId\":\"").append(parent).append("\"");
		}
		return idOf(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/projects/" + f.project() + "/issues").header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body.append("}").toString())));
	}

	private void setStatus(Session caller, UUID issue, String status) throws Exception {
		mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("status", status))).andExpect(status().isOk());
	}

	private UUID id(ResultActions created) throws Exception {
		return idOf(created);
	}

	private UUID idOf(ResultActions created) throws Exception {
		return UUID.fromString(JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id"));
	}

}
