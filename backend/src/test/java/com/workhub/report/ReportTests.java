package com.workhub.report;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
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
class ReportTests {

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID workspace, UUID project, Session po, Session dev) {
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
		return new Fixture(owner, ws, project, po, dev);
	}

	// ---- sprint charts ----

	@Test
	void chartsCombineTheRecordedStateWithAnIdealLine() throws Exception {
		Fixture f = fixture();
		LocalDate start = LocalDate.now();
		LocalDate end = start.plusDays(13);
		UUID sprint = createSprint(f, start, end);
		UUID a = issue(f, "TASK", "A", 5);
		UUID b = issue(f, "TASK", "B", 3);
		add(f, sprint, a, b);

		// Before the start there is nothing to draw.
		charts(f, sprint).andExpect(jsonPath("$.started").value(false)).andExpect(jsonPath("$.days.length()").value(0));

		start(f, sprint);
		setStatus(f, a, "DONE");
		setStatus(f, b, "IN_PROGRESS");

		int workdays = 0;
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
			if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
				workdays++;
			}
		}
		String body = charts(f, sprint).andExpect(status().isOk())
			.andExpect(jsonPath("$.started").value(true))
			.andExpect(jsonPath("$.workdays").value(workdays))
			.andExpect(jsonPath("$.committedPoints").value(8))
			.andExpect(jsonPath("$.days.length()").value(14))
			.andExpect(jsonPath("$.days[0].actual").value(true))
			.andExpect(jsonPath("$.days[0].scopePoints").value(8))
			.andExpect(jsonPath("$.days[0].donePoints").value(5))
			.andExpect(jsonPath("$.days[0].remainingPoints").value(3))
			.andExpect(jsonPath("$.days[0].doneIssues").value(1))
			.andExpect(jsonPath("$.days[0].inProgressIssues").value(1))
			.andExpect(jsonPath("$.days[1].actual").value(false))
			.andExpect(jsonPath("$.days[1].scopePoints").doesNotExist())
			.andExpect(jsonPath("$.days[13].idealPoints").value(0.0))
			.andReturn().getResponse().getContentAsString();
		// The ideal line only ever goes down.
		double previous = Double.MAX_VALUE;
		for (int i = 0; i < 14; i++) {
			double ideal = ((Number) JsonPath.read(body, "$.days[" + i + "].idealPoints")).doubleValue();
			assertThat(ideal).isLessThanOrEqualTo(previous).isLessThanOrEqualTo(8.0);
			previous = ideal;
		}
	}

	@Test
	void scopeAddedAfterTheStartShowsInTheScopeLineButNotInTheCommitment() throws Exception {
		Fixture f = fixture();
		UUID sprint = createSprint(f, LocalDate.now(), LocalDate.now().plusDays(6));
		add(f, sprint, issue(f, "TASK", "A", 5));
		start(f, sprint);
		add(f, sprint, issue(f, "TASK", "Late", 3));

		charts(f, sprint).andExpect(jsonPath("$.committedPoints").value(5))
			.andExpect(jsonPath("$.days[0].scopePoints").value(8))
			.andExpect(jsonPath("$.days[0].remainingPoints").value(8));
	}

	@Test
	void outsidersCannotSeeReports() throws Exception {
		Fixture f = fixture();
		UUID sprint = createSprint(f, LocalDate.now(), LocalDate.now().plusDays(6));
		Session outsider = TestApi.register(mockMvc);

		mockMvc.perform(get("/api/sprints/" + sprint + "/charts").header("Authorization", outsider.bearer()))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));
		for (String path : new String[] {"velocity", "lead-time", "workload", "bug-trend"}) {
			mockMvc.perform(get("/api/projects/" + f.project() + "/reports/" + path)
				.header("Authorization", outsider.bearer())).andExpect(status().isNotFound());
		}
	}

	// ---- velocity ----

	@Test
	void velocityListsCompletedSprintsAndAveragesTheLastThree() throws Exception {
		Fixture f = fixture();
		report(f, "velocity").andExpect(jsonPath("$.sprints.length()").value(0))
			.andExpect(jsonPath("$.average").doesNotExist());

		runSprint(f, "S1", 5, true);
		runSprint(f, "S2", 3, true);
		runSprint(f, "S3", 8, false); // committed 8, nothing finished
		runSprint(f, "S4", 4, true);

		report(f, "velocity").andExpect(jsonPath("$.sprints.length()").value(4))
			.andExpect(jsonPath("$.sprints[0].name").value("S1"))
			.andExpect(jsonPath("$.sprints[0].completedPoints").value(5))
			.andExpect(jsonPath("$.sprints[2].committedPoints").value(8))
			.andExpect(jsonPath("$.sprints[2].completedPoints").value(0))
			.andExpect(jsonPath("$.sprints[3].name").value("S4"))
			// Last three: 3, 0, 4.
			.andExpect(jsonPath("$.average").value(2.3));
	}

	// ---- lead time, workload, bugs ----

	@Test
	void leadAndCycleTimeCoverIssuesFinishedInTheWindow() throws Exception {
		Fixture f = fixture();
		UUID started = issue(f, "TASK", "Started first", 2);
		UUID direct = issue(f, "TASK", "Straight to done", 1);
		issue(f, "TASK", "Still open", 1);
		setStatus(f, started, "IN_PROGRESS");
		setStatus(f, started, "DONE");
		setStatus(f, direct, "DONE");

		report(f, "lead-time?days=30").andExpect(status().isOk())
			.andExpect(jsonPath("$.days").value(30))
			.andExpect(jsonPath("$.summary.count").value(2))
			.andExpect(jsonPath("$.points.length()").value(2))
			.andExpect(jsonPath("$.points[0].key").value(org.hamcrest.Matchers.matchesPattern("[A-Z0-9]+-\\d+")))
			.andExpect(jsonPath("$.summary.avgLead").isNumber())
			.andExpect(jsonPath("$.summary.avgCycle").isNumber());
		// An out-of-range window is clamped.
		report(f, "lead-time?days=5").andExpect(jsonPath("$.days").value(7));
	}

	@Test
	void workloadCountsOpenIssuesPerAssigneeAndSkipsDoneOnes() throws Exception {
		Fixture f = fixture();
		UUID mine = issue(f, "TASK", "Mine", 5);
		UUID doing = issue(f, "TASK", "Doing", 3);
		UUID done = issue(f, "TASK", "Done", 2);
		issue(f, "TASK", "Nobody's", 1);
		for (UUID id : new UUID[] {mine, doing, done}) {
			mockMvc.perform(put("/api/issues/" + id + "/assignee").header("Authorization", f.po().bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + f.dev().userId() + "\"}"))
				.andExpect(status().isOk());
		}
		setStatus(f, doing, "IN_PROGRESS");
		setStatus(f, done, "DONE");

		report(f, "workload").andExpect(jsonPath("$.rows.length()").value(2))
			.andExpect(jsonPath("$.rows[0].userId").value(f.dev().userId().toString()))
			.andExpect(jsonPath("$.rows[0].todoIssues").value(1))
			.andExpect(jsonPath("$.rows[0].inProgressIssues").value(1))
			.andExpect(jsonPath("$.rows[0].openPoints").value(8))
			.andExpect(jsonPath("$.rows[1].userId").doesNotExist())
			.andExpect(jsonPath("$.rows[1].todoIssues").value(1));
	}

	@Test
	void bugTrendCountsCreatedResolvedAndStillOpenPerWeek() throws Exception {
		Fixture f = fixture();
		UUID fixed = issue(f, "BUG", "Fixed", null);
		issue(f, "BUG", "Open", null);
		issue(f, "TASK", "Not a bug", null);
		setStatus(f, fixed, "DONE");

		report(f, "bug-trend?weeks=4").andExpect(jsonPath("$.weeks.length()").value(4))
			.andExpect(jsonPath("$.weeks[3].created").value(2))
			.andExpect(jsonPath("$.weeks[3].resolved").value(1))
			.andExpect(jsonPath("$.weeks[3].open").value(1))
			.andExpect(jsonPath("$.weeks[2].created").value(0))
			.andExpect(jsonPath("$.weeks[2].open").value(0));
	}

	// ---- helpers ----

	/** Creates a sprint with one issue of {@code points}, starts it, optionally finishes the issue, completes it. */
	private void runSprint(Fixture f, String name, int points, boolean finish) throws Exception {
		LocalDate today = LocalDate.now();
		UUID sprint = createSprint(f, name, today, today.plusDays(6));
		UUID issue = issue(f, "TASK", name + " work", points);
		add(f, sprint, issue);
		start(f, sprint);
		if (finish) {
			setStatus(f, issue, "DONE");
		}
		mockMvc.perform(post("/api/sprints/" + sprint + "/complete").header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"goalOutcome\":\"ACHIEVED\",\"unfinishedTo\":\"BACKLOG\"}")).andExpect(status().isOk());
	}

	private ResultActions charts(Fixture f, UUID sprint) throws Exception {
		return mockMvc.perform(get("/api/sprints/" + sprint + "/charts").header("Authorization", f.dev().bearer()));
	}

	private ResultActions report(Fixture f, String path) throws Exception {
		return mockMvc.perform(get("/api/projects/" + f.project() + "/reports/" + path)
			.header("Authorization", f.dev().bearer()));
	}

	private UUID createSprint(Fixture f, LocalDate start, LocalDate end) throws Exception {
		return createSprint(f, "Sprint", start, end);
	}

	private UUID createSprint(Fixture f, String name, LocalDate start, LocalDate end) throws Exception {
		return created(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints")
			.header("Authorization", f.po().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}")));
	}

	private void add(Fixture f, UUID sprint, UUID... ids) throws Exception {
		String list = java.util.Arrays.stream(ids).map((id) -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
		mockMvc.perform(post("/api/sprints/" + sprint + "/issues").header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"issueIds\":[" + list + "],\"includeChildren\":false}"))
			.andExpect(status().isOk());
	}

	private void start(Fixture f, UUID sprint) throws Exception {
		mockMvc.perform(post("/api/sprints/" + sprint + "/start").header("Authorization", f.po().bearer()))
			.andExpect(status().isOk());
	}

	private UUID issue(Fixture f, String type, String title, Integer points) throws Exception {
		String body = "{\"type\":\"" + type + "\",\"title\":\"" + title + "\""
				+ ((points != null) ? ",\"storyPoints\":" + points : "") + "}";
		return created(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", f.po().bearer()).contentType(MediaType.APPLICATION_JSON).content(body)));
	}

	private void setStatus(Fixture f, UUID issue, String status) throws Exception {
		mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("status", status))).andExpect(status().isOk());
	}

	private UUID created(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id"));
	}

}
