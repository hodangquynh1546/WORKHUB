package com.workhub.notification;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.alert.AlertService;
import com.workhub.meeting.MeetingReminderService;
import com.workhub.support.RecordingEmailSender;
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
class NotificationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AlertService alertService;

	@Autowired
	private MeetingReminderService reminders;

	@Autowired
	private RecordingEmailSender emails;

	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, Session dev, Session other,
			Session stakeholder) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		currentProject = project;
		Fixture fixture = new Fixture(owner, ws, team, project, member(owner, ws, project, "DEVELOPER"),
				member(owner, ws, project, "DEVELOPER"), member(owner, ws, project, "STAKEHOLDER"));
		// Joining the project already produced "added to project" notifications; mark them read so tests start clean.
		for (Session s : List.of(fixture.dev(), fixture.other(), fixture.stakeholder())) {
			mockMvc.perform(post("/api/notifications/read-all").header("Authorization", s.bearer())).andExpect(status().isNoContent());
		}
		return fixture;
	}

	private Session member(Session owner, UUID ws, UUID project, String role) throws Exception {
		Session s = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, s, role);
		return s;
	}

	// ---- the notification center ----

	@Test
	void assigningAnIssueNotifiesTheAssigneeButNotTheActor() throws Exception {
		Fixture f = fixture();
		UUID issue = issue(f, f.owner(), "Build login", null, null);
		long emailsBefore = emails.countTo(f.dev().email());

		assign(f.owner(), issue, f.dev()).andExpect(status().isOk());
		assign(f.dev(), issue, f.dev()).andExpect(status().isOk());

		list(f.dev(), "unread=true").andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].type").value("ISSUE_ASSIGNED"))
			.andExpect(jsonPath("$.items[0].title").value(org.hamcrest.Matchers.endsWith(": Build login")))
			.andExpect(jsonPath("$.items[0].link").value("/issues/" + issue))
			.andExpect(jsonPath("$.items[0].read").value(false))
			.andExpect(jsonPath("$.unread").value(1));
		// Assigning something to yourself says nothing, and strangers see nothing.
		list(f.owner(), "unread=true").andExpect(jsonPath("$.items.length()").value(0));
		list(f.other(), "unread=true").andExpect(jsonPath("$.items.length()").value(0));
		// ISSUE_ASSIGNED is emailed by default.
		assertThat(emails.countTo(f.dev().email())).isEqualTo(emailsBefore + 1);
	}

	@Test
	void notificationsCanBeReadOneByOneOrAllAtOnce() throws Exception {
		Fixture f = fixture();
		UUID a = issue(f, f.owner(), "A", null, null);
		UUID b = issue(f, f.owner(), "B", null, null);
		assign(f.owner(), a, f.dev());
		assign(f.owner(), b, f.dev());

		String body = list(f.dev(), "").andReturn().getResponse().getContentAsString();
		String first = JsonPath.read(body, "$.items[0].id");
		mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.unread").value(2));

		// Someone else's notification is a 404, not a 403.
		mockMvc.perform(post("/api/notifications/" + first + "/read").header("Authorization", f.other().bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/notifications/" + first + "/read").header("Authorization", f.dev().bearer()))
			.andExpect(status().isNoContent());
		list(f.dev(), "unread=true").andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.unread").value(1));
		mockMvc.perform(post("/api/notifications/read-all").header("Authorization", f.dev().bearer()))
			.andExpect(status().isNoContent());
		list(f.dev(), "unread=true").andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.unread").value(0));
		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='ISSUE_ASSIGNED')]", org.hamcrest.Matchers.hasSize(2)));
	}

	@Test
	void statusChangesNotifyTheAssigneeAndReporterExceptTheActor() throws Exception {
		Fixture f = fixture();
		UUID issue = issue(f, f.other(), "Task", null, null);
		assign(f.other(), issue, f.dev());

		setStatus(f.dev(), issue, "IN_PROGRESS").andExpect(status().isOk());

		// The reporter hears about it; the person who made the change (and assignee) does not hear about their own action.
		list(f.other(), "").andExpect(jsonPath("$.items[0].type").value("ISSUE_STATUS_CHANGED"))
			.andExpect(jsonPath("$.items[0].title").value(org.hamcrest.Matchers.containsString("Đang làm")));
		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='ISSUE_STATUS_CHANGED')]").isEmpty());
	}

	@Test
	void commentsNotifyWatchersAndMentionedPeople() throws Exception {
		Fixture f = fixture();
		UUID issue = issue(f, f.dev(), "Task", null, null);
		assign(f.dev(), issue, f.other());

		comment(f.owner(), issue, "Looking good", List.of(f.stakeholder().userId())).andExpect(status().isCreated());

		list(f.stakeholder(), "").andExpect(jsonPath("$.items[0].type").value("MENTIONED"));
		list(f.other(), "").andExpect(jsonPath("$.items[?(@.type=='ISSUE_COMMENTED')]").isNotEmpty());
		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='ISSUE_COMMENTED')]").isNotEmpty());
		// Mentioning someone and being the assignee is one notification, the mention.
		comment(f.owner(), issue, "Hi", List.of(f.other().userId())).andExpect(status().isCreated());
		list(f.other(), "").andExpect(jsonPath("$.items[0].type").value("MENTIONED"));

		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		comment(f.owner(), issue, "x", List.of(outsider.userId())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MENTION_NOT_MEMBER"));
	}

	@Test
	void beingAddedToAProjectOrSprintNotifies() throws Exception {
		Fixture f = fixture();
		Session late = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		TestApi.addToProject(mockMvc, f.owner(), f.project(), late, "DEVELOPER");
		list(late, "").andExpect(jsonPath("$.items[0].type").value("INVITED_TO_PROJECT"));

		UUID issue = issue(f, f.owner(), "In sprint", null, null);
		UUID sprint = UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Sprint 1\",\"startDate\":\"" + LocalDate.now() + "\",\"endDate\":\"" + LocalDate.now().plusDays(13) + "\"}"))
			.andReturn().getResponse().getContentAsString(), "$.id"));
		mockMvc.perform(post("/api/sprints/" + sprint + "/issues").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"issueIds\":[\"" + issue + "\"],\"includeChildren\":false}"));
		mockMvc.perform(post("/api/sprints/" + sprint + "/start").header("Authorization", f.owner().bearer())).andExpect(status().isOk());

		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='SPRINT_STARTED')]").isNotEmpty());
		list(f.owner(), "").andExpect(jsonPath("$.items[?(@.type=='SPRINT_STARTED')]").isEmpty());
	}

	@Test
	void meetingInvitationsAndCancellationsNotifyTheInvited() throws Exception {
		Fixture f = fixture();
		Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
		UUID meeting = UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/meetings")
			.header("Authorization", f.dev().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Sync\",\"type\":\"GENERAL\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + start.plusSeconds(3600)
					+ "\",\"attendeeIds\":[\"" + f.other().userId() + "\"]}")).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id"));
		list(f.other(), "").andExpect(jsonPath("$.items[0].type").value("MEETING_INVITED"))
			.andExpect(jsonPath("$.items[0].link").value("/projects/" + f.project() + "/meetings/" + meeting));

		mockMvc.perform(post("/api/meetings/" + meeting + "/cancel").header("Authorization", f.dev().bearer())).andExpect(status().isOk());
		list(f.other(), "").andExpect(jsonPath("$.items[0].type").value("MEETING_CANCELLED"));
	}

	// ---- preferences ----

	@Test
	void usersChooseWhichChannelsEachTypeUses() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(get("/api/notification-preferences").header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(NotificationType.values().length))
			.andExpect(jsonPath("$[?(@.type=='ISSUE_ASSIGNED')].email").value(true))
			.andExpect(jsonPath("$[?(@.type=='ISSUE_COMMENTED')].email").value(false))
			.andExpect(jsonPath("$[?(@.type=='ALERT_OVERDUE')].alert").value(true));

		// In-app off, email stays: no row, but the email goes out.
		setPreference(f.dev(), "ISSUE_ASSIGNED", false, true).andExpect(status().isOk());
		UUID issue = issue(f, f.owner(), "A", null, null);
		long before = emails.countTo(f.dev().email());
		assign(f.owner(), issue, f.dev());
		list(f.dev(), "unread=true").andExpect(jsonPath("$.items.length()").value(0));
		assertThat(emails.countTo(f.dev().email())).isEqualTo(before + 1);

		// Both off: nothing at all.
		setPreference(f.dev(), "ISSUE_ASSIGNED", false, false);
		UUID second = issue(f, f.owner(), "B", null, null);
		assign(f.owner(), second, f.dev());
		list(f.dev(), "unread=true").andExpect(jsonPath("$.items.length()").value(0));
		assertThat(emails.countTo(f.dev().email())).isEqualTo(before + 1);
	}

	// ---- progress alerts ----

	@Test
	void overdueAndDueSoonWorkIsReportedAndNotifiedOncePerIssue() throws Exception {
		Fixture f = fixture();
		LocalDate today = LocalDate.now();
		UUID overdue = issue(f, f.owner(), "Late", today.minusDays(3), f.dev());
		UUID soon = issue(f, f.owner(), "Soon", today.plusDays(1), f.dev());
		issue(f, f.owner(), "Far", today.plusDays(20), f.dev());
		UUID done = issue(f, f.owner(), "Done late", today.minusDays(5), f.dev());
		setStatus(f.dev(), done, "DONE");

		String body = alerts(f.stakeholder()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$[?(@.kind=='ALERT_OVERDUE')].subjectId")).containsExactly(overdue.toString());
		assertThat(JsonPath.<List<String>>read(body, "$[?(@.kind=='ALERT_DUE_SOON')].subjectId")).containsExactly(soon.toString());

		alertService.run();
		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='ALERT_OVERDUE')].alert").value(true));
		long count = (Integer) JsonPath.read(list(f.dev(), "").andReturn().getResponse().getContentAsString(), "$.total");
		// The job runs every hour: the same findings must not pile up.
		alertService.run();
		alertService.run();
		list(f.dev(), "").andExpect(jsonPath("$.total").value((int) count));
	}

	@Test
	void thresholdsAreConfigurableAndOnlyManagersChangeThem() throws Exception {
		Fixture f = fixture();
		LocalDate today = LocalDate.now();
		issue(f, f.owner(), "Late", today.minusDays(1), f.dev());
		issue(f, f.owner(), "Soon", today.plusDays(1), f.dev());

		settings(f.stakeholder()).andExpect(status().isOk()).andExpect(jsonPath("$.dueSoonDays").value(2))
			.andExpect(jsonPath("$.overdueEnabled").value(true));
		putSettings(f.dev(), "true", 2, 2, 40, "true", 10, 20, 5).andExpect(status().isForbidden());
		putSettings(f.owner(), "true", 31, 2, 40, "true", 10, 20, 5).andExpect(status().isBadRequest());
		// Due-soon off and overdue off: nothing left to report.
		putSettings(f.owner(), "false", 0, 2, 40, "true", 10, 20, 5).andExpect(status().isOk())
			.andExpect(jsonPath("$.dueSoonDays").value(0));
		alerts(f.dev()).andExpect(jsonPath("$[?(@.kind=='ALERT_OVERDUE' || @.kind=='ALERT_DUE_SOON')]").isEmpty());
		putSettings(f.owner(), "true", 0, 2, 40, "true", 10, 20, 5).andExpect(status().isOk());
		alerts(f.dev()).andExpect(jsonPath("$[?(@.kind=='ALERT_OVERDUE')]").isNotEmpty())
			.andExpect(jsonPath("$[?(@.kind=='ALERT_DUE_SOON')]").isEmpty());

		Session outsider = TestApi.register(mockMvc);
		alerts(outsider).andExpect(status().isNotFound());
	}

	@Test
	void blockedWorkAlertsTheLeadersAndTheAssignee() throws Exception {
		Fixture f = fixture();
		UUID issue = issue(f, f.owner(), "Stuck", null, f.dev());

		mockMvc.perform(put("/api/issues/" + issue + "/blocked").header("Authorization", f.other().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true,\"reason\":\"waiting on API\"}"))
			.andExpect(status().isOk());

		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='TASK_BLOCKED')].body").value("waiting on API"));
		list(f.owner(), "").andExpect(jsonPath("$.items[?(@.type=='TASK_BLOCKED')]").isNotEmpty());
		list(f.stakeholder(), "").andExpect(jsonPath("$.items[?(@.type=='TASK_BLOCKED')]").isEmpty());
		alerts(f.dev()).andExpect(jsonPath("$[?(@.kind=='TASK_BLOCKED')]").isNotEmpty());
	}

	@Test
	void anOverloadedMemberIsFlaggedToTheLeaders() throws Exception {
		Fixture f = fixture();
		for (int i = 0; i < 4; i++) {
			issue(f, f.owner(), "Work " + i, null, f.dev());
		}
		putSettings(f.owner(), "true", 2, 2, 40, "true", 3, 20, 5).andExpect(status().isOk());

		alerts(f.dev()).andExpect(jsonPath("$[?(@.kind=='ALERT_OVERLOAD')].title", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("4 việc"))));
		alertService.run();
		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='ALERT_OVERLOAD')]").isNotEmpty());
		list(f.owner(), "").andExpect(jsonPath("$.items[?(@.type=='ALERT_OVERLOAD')]").isNotEmpty());
	}

	@Test
	void aSprintWithLittleTimeAndMuchLeftIsAtRisk() throws Exception {
		Fixture f = fixture();
		UUID done = issue(f, f.owner(), "Done", null, null);
		UUID open = issue(f, f.owner(), "Open", null, null);
		// 2 points done, 8 open, sprint ends tomorrow.
		setPoints(f, done, 2);
		setPoints(f, open, 8);
		UUID sprint = UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Sprint 1\",\"startDate\":\"" + LocalDate.now() + "\",\"endDate\":\"" + LocalDate.now().plusDays(1) + "\"}"))
			.andReturn().getResponse().getContentAsString(), "$.id"));
		mockMvc.perform(post("/api/sprints/" + sprint + "/issues").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"issueIds\":[\"" + done + "\",\"" + open + "\"],\"includeChildren\":false}"));
		mockMvc.perform(post("/api/sprints/" + sprint + "/start").header("Authorization", f.owner().bearer())).andExpect(status().isOk());
		setStatus(f.owner(), done, "DONE");

		alerts(f.dev()).andExpect(jsonPath("$[?(@.kind=='ALERT_SPRINT_AT_RISK')].title", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("80% chưa xong"))))
			.andExpect(jsonPath("$[?(@.kind=='ALERT_SPRINT_AT_RISK')].severity").value("WARNING"));

		// Scope added after the start: 5 points on a commitment of 10 is +50%.
		UUID late = issue(f, f.owner(), "Late scope", null, null);
		setPoints(f, late, 5);
		mockMvc.perform(post("/api/sprints/" + sprint + "/issues").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"issueIds\":[\"" + late + "\"],\"includeChildren\":false}"));
		alerts(f.dev()).andExpect(jsonPath("$[?(@.kind=='ALERT_SCOPE_CHANGE')].title", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("+50%"))));

		alertService.run();
		list(f.owner(), "").andExpect(jsonPath("$.items[?(@.type=='ALERT_SPRINT_AT_RISK')]").isNotEmpty());
		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='ALERT_SPRINT_AT_RISK')]").isEmpty());
	}

	@Test
	void meetingRemindersGoOutOnceWhenTheTimeComes() throws Exception {
		Fixture f = fixture();
		Instant soon = Instant.now().plus(10, ChronoUnit.MINUTES);
		Instant later = Instant.now().plus(3, ChronoUnit.HOURS);
		UUID near = meeting(f, "Soon", soon, 15, List.of(f.other().userId(), f.stakeholder().userId()));
		meeting(f, "Later", later, 15, List.of(f.other().userId()));
		mockMvc.perform(put("/api/meetings/" + near + "/response").header("Authorization", f.stakeholder().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"DECLINED\"}")).andExpect(status().isOk());

		// At least ours: on the persistent PostgreSQL database meetings left by an earlier run may be due as well.
		assertThat(reminders.run()).isGreaterThanOrEqualTo(1);
		list(f.other(), "").andExpect(jsonPath("$.items[?(@.type=='MEETING_REMINDER')].title").value("Sắp họp: Soon"));
		list(f.dev(), "").andExpect(jsonPath("$.items[?(@.type=='MEETING_REMINDER')]").isNotEmpty());
		// Someone who declined is left alone.
		list(f.stakeholder(), "").andExpect(jsonPath("$.items[?(@.type=='MEETING_REMINDER')]").isEmpty());
		// At least ours: on the persistent PostgreSQL database meetings left by an earlier run may be due as well.
		assertThat(reminders.run()).isGreaterThanOrEqualTo(1);
		list(f.other(), "").andExpect(jsonPath("$.items[?(@.type=='MEETING_REMINDER')]", org.hamcrest.Matchers.hasSize(1)));
	}

	// ---- helpers ----

	private UUID meeting(Fixture f, String title, Instant start, int reminder, List<UUID> attendees) throws Exception {
		String ids = attendees.stream().map((id) -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
		return UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/meetings")
			.header("Authorization", f.dev().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"" + title + "\",\"type\":\"GENERAL\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + start.plusSeconds(1800)
					+ "\",\"reminderMinutes\":" + reminder + ",\"attendeeIds\":[" + ids + "]}")).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private UUID issue(Fixture f, Session creator, String title, LocalDate due, Session assignee) throws Exception {
		String body = "{\"type\":\"TASK\",\"title\":\"" + title + "\"" + ((due != null) ? ",\"dueDate\":\"" + due + "\"" : "")
				+ ((assignee != null) ? ",\"assigneeId\":\"" + assignee.userId() + "\"" : "") + "}";
		return UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", creator.bearer()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private void setPoints(Fixture f, UUID issue, int points) throws Exception {
		mockMvc.perform(put("/api/issues/" + issue).header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"x\",\"priority\":\"MEDIUM\",\"refinement\":\"NEW\",\"storyPoints\":" + points + "}"))
			.andExpect(status().isOk());
	}

	private ResultActions assign(Session caller, UUID issue, Session assignee) throws Exception {
		return mockMvc.perform(put("/api/issues/" + issue + "/assignee").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + assignee.userId() + "\"}"));
	}

	private ResultActions setStatus(Session caller, UUID issue, String status) throws Exception {
		return mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
	}

	private ResultActions comment(Session caller, UUID issue, String text, List<UUID> mentions) throws Exception {
		String ids = mentions.stream().map((id) -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
		return mockMvc.perform(post("/api/issues/" + issue + "/comments").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + text + "\",\"mentions\":[" + ids + "]}"));
	}

	private ResultActions list(Session caller, String query) throws Exception {
		return mockMvc.perform(get("/api/notifications" + (query.isEmpty() ? "" : "?" + query)).header("Authorization", caller.bearer()))
			.andExpect(status().isOk());
	}

	private ResultActions setPreference(Session caller, String type, boolean inApp, boolean email) throws Exception {
		return mockMvc.perform(put("/api/notification-preferences").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"" + type + "\",\"inApp\":" + inApp + ",\"email\":" + email + "}"));
	}

	private ResultActions alerts(Session caller) throws Exception {
		return mockMvc.perform(get("/api/projects/" + currentProject + "/alerts").header("Authorization", caller.bearer()));
	}

	private ResultActions settings(Session caller) throws Exception {
		return mockMvc.perform(get("/api/projects/" + currentProject + "/alert-settings").header("Authorization", caller.bearer()));
	}

	private ResultActions putSettings(Session caller, String overdue, int dueSoon, int riskDays, int riskPct, String blocked,
			int overload, int scope, int noProgress) throws Exception {
		return mockMvc.perform(put("/api/projects/" + currentProject + "/alert-settings").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"overdueEnabled\":" + overdue + ",\"dueSoonDays\":" + dueSoon + ",\"sprintRiskDaysLeft\":" + riskDays
					+ ",\"sprintRiskOpenPct\":" + riskPct + ",\"blockedEnabled\":" + blocked + ",\"overloadOpenIssues\":" + overload
					+ ",\"scopeChangePct\":" + scope + ",\"noProgressDays\":" + noProgress + "}"));
	}

	/** The project of the fixture the current test built (alert endpoints are project-scoped). */
	private UUID currentProject;

}
