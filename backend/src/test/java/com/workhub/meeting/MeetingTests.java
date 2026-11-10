package com.workhub.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
class MeetingTests {

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID workspace, UUID project, Session dev, Session other,
			Session stakeholder) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		return new Fixture(owner, ws, project, member(owner, ws, project, "DEVELOPER"),
				member(owner, ws, project, "DEVELOPER"), member(owner, ws, project, "STAKEHOLDER"));
	}

	private Session member(Session owner, UUID ws, UUID project, String role) throws Exception {
		Session s = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, s, role);
		return s;
	}

	private static final Instant BASE = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(2, ChronoUnit.DAYS);

	// ---- templates ----

	@Test
	void everyMeetingTypeHasATemplate() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(get("/api/meeting-templates").header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(MeetingType.values().length))
			.andExpect(jsonPath("$[?(@.type=='DAILY_SCRUM')].durationMinutes").value(15))
			.andExpect(jsonPath("$[?(@.type=='SPRINT_RETROSPECTIVE')].agenda").isNotEmpty());
		mockMvc.perform(get("/api/meeting-templates")).andExpect(status().isUnauthorized());
	}

	// ---- scheduling ----

	@Test
	void membersScheduleMeetingsAndEveryoneInTheProjectCanRead() throws Exception {
		Fixture f = fixture();
		UUID sprint = sprint(f, "Sprint 1");

		UUID meeting = created(create(f.dev(), f.project(), body("Planning", "SPRINT_PLANNING", BASE, 2, sprint, null,
				List.of(f.other().userId(), f.stakeholder().userId()))));
		fetch("/api/meetings/" + meeting, f.stakeholder()).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Planning"))
			.andExpect(jsonPath("$.type").value("SPRINT_PLANNING"))
			.andExpect(jsonPath("$.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.organizer.id").value(f.dev().userId().toString()))
			.andExpect(jsonPath("$.sprint.name").value("Sprint 1"))
			.andExpect(jsonPath("$.attendees.length()").value(2))
			.andExpect(jsonPath("$.myResponse").value("INVITED"))
			.andExpect(jsonPath("$.canEdit").value(false))
			.andExpect(jsonPath("$.canTakeNotes").value(false));
		fetch("/api/meetings/" + meeting, f.dev()).andExpect(jsonPath("$.canEdit").value(true))
			.andExpect(jsonPath("$.canTakeNotes").value(true)).andExpect(jsonPath("$.myResponse").doesNotExist());

		create(f.stakeholder(), f.project(), body("Nope", "GENERAL", BASE, 1, null, null, List.of())).andExpect(status().isForbidden());
		Session outsider = TestApi.register(mockMvc);
		fetch("/api/meetings/" + meeting, outsider).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
		fetch("/api/projects/" + f.project() + "/meetings", outsider).andExpect(status().isNotFound());
	}

	@Test
	void meetingDetailsAreValidated() throws Exception {
		Fixture f = fixture();
		Fixture other = fixture();
		UUID foreignSprint = sprint(other, "Theirs");
		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		create(f.dev(), f.project(), raw("X", "GENERAL", BASE, BASE.minusSeconds(60), null, null, List.of()))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TIME"));
		create(f.dev(), f.project(), raw("X", "GENERAL", BASE, BASE.plus(25, ChronoUnit.HOURS), null, null, List.of()))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEETING_TOO_LONG"));
		create(f.dev(), f.project(), body("X", "GENERAL", BASE, 1, foreignSprint, null, List.of()))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SPRINT_NOT_IN_PROJECT"));
		create(f.dev(), f.project(), body("X", "GENERAL", BASE, 1, null, null, List.of(outsider.userId())))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ATTENDEE_NOT_MEMBER"));
		create(f.dev(), f.project(), body("X", "GENERAL", BASE, 1, null, outsider.userId(), List.of()))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ORGANIZER_NOT_MEMBER"));
		create(f.dev(), f.project(), "{\"title\":\"X\",\"type\":\"GENERAL\",\"startsAt\":\"" + BASE + "\",\"endsAt\":\""
				+ BASE.plusSeconds(3600) + "\",\"onlineLink\":\"javascript:alert(1)\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.onlineLink").exists());
		create(f.dev(), f.project(), body(" ", "GENERAL", BASE, 1, null, null, List.of())).andExpect(status().isBadRequest());
	}

	@Test
	void onlyTheOrganizerOrAManagerChangesCancelsOrDeletes() throws Exception {
		Fixture f = fixture();
		UUID meeting = created(create(f.dev(), f.project(), body("Sync", "GENERAL", BASE, 1, null, null,
				List.of(f.other().userId()))));
		// The other attendee replies, then the organizer changes the list: the reply of someone who stays is kept.
		respond(f.other(), meeting, "ACCEPTED").andExpect(status().isOk()).andExpect(jsonPath("$.myResponse").value("ACCEPTED"));

		update(f.other(), meeting, body("Renamed", "GENERAL", BASE, 1, null, null, List.of())).andExpect(status().isForbidden());
		act("/api/meetings/" + meeting + "/cancel", f.other()).andExpect(status().isForbidden());
		del("/api/meetings/" + meeting, f.other()).andExpect(status().isForbidden());

		update(f.dev(), meeting, body("Renamed", "TECHNICAL", BASE.plusSeconds(1800), 2, null, null,
				List.of(f.other().userId(), f.stakeholder().userId())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed"))
			.andExpect(jsonPath("$.type").value("TECHNICAL"))
			.andExpect(jsonPath("$.attendees.length()").value(2))
			.andExpect(jsonPath("$.attendees[?(@.user.id=='" + f.other().userId() + "')].response").value("ACCEPTED"));
		// A project manager (the workspace owner) may edit someone else's meeting.
		update(f.owner(), meeting, body("By manager", "GENERAL", BASE, 1, null, f.other().userId(), List.of())).andExpect(status().isOk())
			.andExpect(jsonPath("$.organizer.id").value(f.other().userId().toString()))
			.andExpect(jsonPath("$.attendees.length()").value(0));

		act("/api/meetings/" + meeting + "/cancel", f.other()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		act("/api/meetings/" + meeting + "/reopen", f.other()).andExpect(jsonPath("$.status").value("SCHEDULED"));
		del("/api/meetings/" + meeting, f.other()).andExpect(status().isNoContent());
		fetch("/api/meetings/" + meeting, f.dev()).andExpect(status().isNotFound());
	}

	@Test
	void onlyInvitedPeopleCanReply() throws Exception {
		Fixture f = fixture();
		UUID meeting = created(create(f.dev(), f.project(), body("Sync", "GENERAL", BASE, 1, null, null, List.of(f.stakeholder().userId()))));

		respond(f.stakeholder(), meeting, "DECLINED").andExpect(status().isOk());
		respond(f.other(), meeting, "ACCEPTED").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AN_ATTENDEE"));
		list(f.stakeholder(), f, "").andExpect(jsonPath("$[0].myResponse").value("DECLINED"))
			.andExpect(jsonPath("$[0].attendeeCount").value(1));
	}

	// ---- lists ----

	@Test
	void meetingsAreFilteredByWindowTypeAndPerson() throws Exception {
		Fixture f = fixture();
		UUID a = created(create(f.dev(), f.project(), body("A", "DAILY_SCRUM", BASE, 1, null, null, List.of(f.other().userId()))));
		UUID b = created(create(f.dev(), f.project(), body("B", "GENERAL", BASE.plus(3, ChronoUnit.DAYS), 1, null, null, List.of())));
		created(create(f.other(), f.project(), body("C", "GENERAL", BASE.plus(6, ChronoUnit.DAYS), 1, null, null, List.of())));

		list(f.dev(), f, "").andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].id").value(a.toString()));
		list(f.dev(), f, "newestFirst=true").andExpect(jsonPath("$[0].title").value("C"));
		list(f.dev(), f, "from=" + BASE.plus(2, ChronoUnit.DAYS) + "&to=" + BASE.plus(4, ChronoUnit.DAYS))
			.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(b.toString()));
		list(f.dev(), f, "type=DAILY_SCRUM").andExpect(jsonPath("$.length()").value(1));
		// Meetings a person organizes or attends.
		list(f.dev(), f, "personId=" + f.other().userId()).andExpect(jsonPath("$.length()").value(2));
		list(f.dev(), f, "personId=" + f.stakeholder().userId()).andExpect(jsonPath("$.length()").value(0));
		act("/api/meetings/" + b + "/cancel", f.dev());
		list(f.dev(), f, "status=CANCELLED").andExpect(jsonPath("$.length()").value(1));
	}

	// ---- minutes ----

	@Test
	void minutesCarryARevisionSoStaleSavesAreRefused() throws Exception {
		Fixture f = fixture();
		UUID meeting = created(create(f.dev(), f.project(), body("Retro", "SPRINT_RETROSPECTIVE", BASE, 1, null, null, List.of())));

		minutes(f.dev(), meeting, "first notes", 0).andExpect(status().isOk())
			.andExpect(jsonPath("$.minutes").value("first notes")).andExpect(jsonPath("$.minutesRevision").value(1));
		minutes(f.other(), meeting, "stale", 0).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MINUTES_CONFLICT"));
		minutes(f.other(), meeting, "second notes", 1).andExpect(status().isOk()).andExpect(jsonPath("$.minutesRevision").value(2));
		minutes(f.stakeholder(), meeting, "x", 2).andExpect(status().isForbidden());
		fetch("/api/meetings/" + meeting, f.stakeholder()).andExpect(jsonPath("$.minutes").value("second notes"));
	}

	@Test
	void twoSimultaneousMinuteSavesCannotBothWin() throws Exception {
		Fixture f = fixture();
		UUID meeting = created(create(f.dev(), f.project(), body("Retro", "GENERAL", BASE, 1, null, null, List.of())));

		var pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Integer>> saves = List.of(
					() -> minutes(f.dev(), meeting, "from dev", 0).andReturn().getResponse().getStatus(),
					() -> minutes(f.other(), meeting, "from other", 0).andReturn().getResponse().getStatus());
			List<Integer> statuses = pool.invokeAll(saves).stream().map((r) -> {
				try {
					return r.get();
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).collect(Collectors.toList());
			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		}
		finally {
			pool.shutdown();
		}
		fetch("/api/meetings/" + meeting, f.dev()).andExpect(jsonPath("$.minutesRevision").value(1));
	}

	// ---- action items ----

	@Test
	void actionItemsAreTrackedAndCanBecomeTasks() throws Exception {
		Fixture f = fixture();
		UUID meeting = created(create(f.dev(), f.project(), body("Retro", "SPRINT_RETROSPECTIVE", BASE, 1, null, null, List.of())));
		LocalDate due = LocalDate.now().plusDays(10);

		String body = actionItem(f.dev(), meeting, "Write the test plan", f.other().userId(), due).andExpect(status().isCreated())
			.andExpect(jsonPath("$.actionItems.length()").value(1))
			.andExpect(jsonPath("$.actionItems[0].status").value("OPEN"))
			.andExpect(jsonPath("$.actionItems[0].assignee.id").value(f.other().userId().toString()))
			.andExpect(jsonPath("$.actionItems[0].dueDate").value(due.toString()))
			.andReturn().getResponse().getContentAsString();
		String item = JsonPath.read(body, "$.actionItems[0].id");
		actionItem(f.stakeholder(), meeting, "Nope", null, null).andExpect(status().isForbidden());
		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		actionItem(f.dev(), meeting, "Bad owner", outsider.userId(), null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_MEMBER"));
		list(f.dev(), f, "").andExpect(jsonPath("$[0].openActionItems").value(1));

		mockMvc.perform(put("/api/meeting-action-items/" + item).header("Authorization", f.other().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\":\"Write the test plan v2\",\"assigneeId\":\"" + f.other().userId() + "\",\"dueDate\":\"" + due + "\",\"status\":\"DONE\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.actionItems[0].status").value("DONE"))
			.andExpect(jsonPath("$.actionItems[0].text").value("Write the test plan v2"));
		list(f.dev(), f, "").andExpect(jsonPath("$[0].openActionItems").value(0));

		// Turning it into a task creates an issue carrying the owner and deadline.
		act("/api/meeting-action-items/" + item + "/create-task", f.dev()).andExpect(status().isOk())
			.andExpect(jsonPath("$.actionItems[0].issue.key").value(org.hamcrest.Matchers.matchesPattern("[A-Z0-9]+-1")))
			.andExpect(jsonPath("$.actionItems[0].issue.title").value("Write the test plan v2"));
		act("/api/meeting-action-items/" + item + "/create-task", f.dev()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_CONVERTED"));
		mockMvc.perform(get("/api/projects/" + f.project() + "/issues").header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].type").value("TASK"))
			.andExpect(jsonPath("$.items[0].assignee.id").value(f.other().userId().toString()))
			.andExpect(jsonPath("$.items[0].dueDate").value(due.toString()));

		del("/api/meeting-action-items/" + item, f.dev()).andExpect(status().isOk()).andExpect(jsonPath("$.actionItems.length()").value(0));
	}

	// ---- calendar ----

	@Test
	void theCalendarMixesMeetingsSprintsAndDueDates() throws Exception {
		Fixture f = fixture();
		LocalDate today = LocalDate.now();
		UUID sprint = sprint(f, "Sprint 1");
		UUID inRange = created(create(f.dev(), f.project(), body("Planning", "SPRINT_PLANNING", Instant.now().plus(1, ChronoUnit.DAYS), 1, sprint, null, List.of(f.other().userId()))));
		created(create(f.dev(), f.project(), body("Far away", "GENERAL", Instant.now().plus(60, ChronoUnit.DAYS), 1, null, null, List.of())));
		UUID issue = created(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", f.dev().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"Due soon\",\"dueDate\":\"" + today.plusDays(3) + "\",\"assigneeId\":\"" + f.other().userId() + "\"}")));

		String path = "/api/projects/" + f.project() + "/calendar?from=" + today + "&to=" + today.plusDays(30);
		String body = mockMvc.perform(get(path).header("Authorization", f.stakeholder().bearer())).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		List<String> kinds = JsonPath.read(body, "$.events[*].kind");
		assertThat(kinds).containsExactlyInAnyOrder("MEETING", "SPRINT", "DUE_DATE");
		assertThat(JsonPath.<List<String>>read(body, "$.events[?(@.kind=='MEETING')].id")).containsExactly(inRange.toString());
		assertThat(JsonPath.<List<String>>read(body, "$.events[?(@.kind=='DUE_DATE')].id")).containsExactly(issue.toString());
		assertThat(JsonPath.<List<String>>read(body, "$.events[?(@.kind=='DUE_DATE')].title").get(0)).endsWith(" Due soon");

		// Narrowing to a person keeps their meetings and their due dates (the sprint is project-wide).
		String mine = mockMvc.perform(get(path + "&personId=" + f.stakeholder().userId()).header("Authorization", f.dev().bearer()))
			.andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(mine, "$.events[*].kind")).containsExactly("SPRINT");
		String theirs = mockMvc.perform(get(path + "&personId=" + f.other().userId()).header("Authorization", f.dev().bearer()))
			.andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(theirs, "$.events[*].kind")).containsExactlyInAnyOrder("MEETING", "SPRINT", "DUE_DATE");

		mockMvc.perform(get("/api/projects/" + f.project() + "/calendar?from=" + today + "&to=" + today.plusDays(101))
			.header("Authorization", f.dev().bearer())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RANGE"));
		mockMvc.perform(get(path).header("Authorization", TestApi.register(mockMvc).bearer())).andExpect(status().isNotFound());
	}

	// ---- documents ----

	@Test
	void documentsCanBeAttachedToMeetings() throws Exception {
		Fixture f = fixture();
		UUID meeting = created(create(f.dev(), f.project(), body("Review", "SPRINT_REVIEW", BASE, 1, null, null, List.of())));
		Fixture other = fixture();
		UUID foreign = created(create(other.dev(), other.project(), body("Theirs", "GENERAL", BASE, 1, null, null, List.of())));
		UUID doc = created(mockMvc.perform(post("/api/projects/" + f.project() + "/documents")
			.header("Authorization", f.dev().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Review notes\",\"type\":\"SPRINT_REVIEW_REPORT\",\"content\":\"x\"}")));

		link(f.dev(), doc, meeting).andExpect(status().isOk()).andExpect(jsonPath("$.links[0].targetType").value("MEETING"))
			.andExpect(jsonPath("$.links[0].label").value("Review"));
		link(f.dev(), doc, foreign).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LINK_TARGET_NOT_FOUND"));
		mockMvc.perform(get("/api/projects/" + f.project() + "/documents?linkedType=MEETING&linkedId=" + meeting)
			.header("Authorization", f.stakeholder().bearer())).andExpect(jsonPath("$.total").value(1));
	}

	// ---- helpers ----

	private ResultActions create(Session caller, UUID project, String json) throws Exception {
		return mockMvc.perform(post("/api/projects/" + project + "/meetings").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions update(Session caller, UUID meeting, String json) throws Exception {
		return mockMvc.perform(put("/api/meetings/" + meeting).header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions respond(Session caller, UUID meeting, String response) throws Exception {
		return mockMvc.perform(put("/api/meetings/" + meeting + "/response").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"" + response + "\"}"));
	}

	private ResultActions minutes(Session caller, UUID meeting, String text, int base) throws Exception {
		return mockMvc.perform(put("/api/meetings/" + meeting + "/minutes").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":\"" + text + "\",\"baseRevision\":" + base + "}"));
	}

	private ResultActions actionItem(Session caller, UUID meeting, String text, UUID assignee, LocalDate due) throws Exception {
		return mockMvc.perform(post("/api/meetings/" + meeting + "/action-items").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\":\"" + text + "\"" + ((assignee != null) ? ",\"assigneeId\":\"" + assignee + "\"" : "")
					+ ((due != null) ? ",\"dueDate\":\"" + due + "\"" : "") + "}"));
	}

	private ResultActions link(Session caller, UUID doc, UUID meeting) throws Exception {
		return mockMvc.perform(post("/api/documents/" + doc + "/links").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetType\":\"MEETING\",\"targetId\":\"" + meeting + "\"}"));
	}

	private ResultActions list(Session caller, Fixture f, String query) throws Exception {
		return mockMvc.perform(get("/api/projects/" + f.project() + "/meetings" + (query.isEmpty() ? "" : "?" + query))
			.header("Authorization", caller.bearer())).andExpect(status().isOk());
	}

	private ResultActions fetch(String path, Session caller) throws Exception {
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
			.header("Authorization", caller.bearer()));
	}

	private ResultActions act(String path, Session caller) throws Exception {
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
			.header("Authorization", caller.bearer()));
	}

	private ResultActions del(String path, Session caller) throws Exception {
		return mockMvc.perform(delete(path).header("Authorization", caller.bearer()));
	}

	private UUID sprint(Fixture f, String name) throws Exception {
		LocalDate start = LocalDate.now();
		return created(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + start.plusDays(13) + "\"}")));
	}

	private static String body(String title, String type, Instant start, int hours, UUID sprint, UUID organizer,
			List<UUID> attendees) {
		return raw(title, type, start, start.plus(hours, ChronoUnit.HOURS), sprint, organizer, attendees);
	}

	private static String raw(String title, String type, Instant start, Instant end, UUID sprint, UUID organizer,
			List<UUID> attendees) {
		return "{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end
				+ "\"" + ((sprint != null) ? ",\"sprintId\":\"" + sprint + "\"" : "")
				+ ((organizer != null) ? ",\"organizerId\":\"" + organizer + "\"" : "") + ",\"attendeeIds\":["
				+ attendees.stream().map((id) -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
	}

	private UUID created(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id"));
	}

}
