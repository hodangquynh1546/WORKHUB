package com.workhub.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
class AuditTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuditRepository repository;

	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, Session admin, Session member) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		return new Fixture(owner, ws, team, project, admin, member);
	}

	// ---- what gets recorded ----

	@Test
	void administrativeChangesAreRecordedWithWhoWhatAndWhere() throws Exception {
		Fixture f = fixture();
		TestApi.addToProject(mockMvc, f.owner(), f.project(), f.member(), "DEVELOPER");
		mockMvc.perform(put("/api/projects/" + f.project() + "/members/" + f.member().userId()).header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"QA_ENGINEER\"}")).andExpect(status().isNoContent());
		mockMvc.perform(put("/api/projects/" + f.project() + "/status").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());

		String body = workspaceLog(f.owner(), f.workspace(), "").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> actions = JsonPath.read(body, "$.items[*].action");
		assertThat(actions).contains("workspace.create", "team.create", "project.create", "project.member_add", "project.member_role_change",
				"project.status_change", "invitation.create", "invitation.accept");

		workspaceLog(f.owner(), f.workspace(), "action=project.member_role_change").andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].actorId").value(f.owner().userId().toString()))
			.andExpect(jsonPath("$.items[0].targetType").value("USER"))
			.andExpect(jsonPath("$.items[0].targetId").value(f.member().userId().toString()))
			.andExpect(jsonPath("$.items[0].detail").value("role=QA_ENGINEER"))
			.andExpect(jsonPath("$.items[0].projectId").value(f.project().toString()))
			.andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"));
		workspaceLog(f.owner(), f.workspace(), "action=project.status_change").andExpect(jsonPath("$.items[0].detail").value("status=ACTIVE"))
			.andExpect(jsonPath("$.items[0].targetLabel").isNotEmpty());
		// A whole family at once.
		workspaceLog(f.owner(), f.workspace(), "action=project.").andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
	}

	@Test
	void creationsKnowTheirNewRecordAndDeletionsKeepTheName() throws Exception {
		Fixture f = fixture();
		UUID issue = created(mockMvc.perform(post("/api/projects/" + f.project() + "/issues").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TASK\",\"title\":\"Remove me\"}")));
		UUID sprint = created(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Sprint 1\",\"startDate\":\"" + LocalDate.now() + "\",\"endDate\":\"" + LocalDate.now().plusDays(6) + "\"}")));

		mockMvc.perform(delete("/api/issues/" + issue).header("Authorization", f.owner().bearer())).andExpect(status().isNoContent());

		workspaceLog(f.owner(), f.workspace(), "action=sprint.create").andExpect(jsonPath("$.items[0].targetId").value(sprint.toString()))
			.andExpect(jsonPath("$.items[0].targetLabel").value("Sprint 1"));
		// The issue is soft-deleted, yet its key and title are still in the log.
		workspaceLog(f.owner(), f.workspace(), "action=issue.delete").andExpect(jsonPath("$.items[0].targetId").value(issue.toString()))
			.andExpect(jsonPath("$.items[0].targetLabel").value(org.hamcrest.Matchers.endsWith("Remove me")));
	}

	@Test
	void everydayWorkAndFailedRequestsAreNotRecorded() throws Exception {
		Fixture f = fixture();
		long before = repository.count();
		UUID issue = created(mockMvc.perform(post("/api/projects/" + f.project() + "/issues").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TASK\",\"title\":\"Daily\"}")));
		mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
		mockMvc.perform(post("/api/issues/" + issue + "/comments").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\"}")).andExpect(status().isCreated());
		// A refused change leaves no trace: the member may not change the status.
		mockMvc.perform(put("/api/projects/" + f.project() + "/status").header("Authorization", f.member().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());

		assertThat(repository.count()).isEqualTo(before);
	}

	@Test
	void signInsAreRecordedAndFailuresOnlyForRealAccounts() throws Exception {
		Session user = TestApi.register(mockMvc);
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + user.email() + "\",\"password\":\"" + TestApi.PASSWORD + "\"}")).andExpect(status().isOk());
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + user.email() + "\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized());
		long before = repository.count();
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"x\"}")).andExpect(status().isUnauthorized());
		assertThat(repository.count()).isEqualTo(before);
		mockMvc.perform(post("/api/me/password").header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + TestApi.PASSWORD + "\",\"newPassword\":\"AnotherPass123!\"}")).andExpect(status().isOk());

		// Nobody's password or token ever reaches the log, and the person sees their own trail.
		String body = mockMvc.perform(get("/api/me/audit-log").header("Authorization", user.bearer())).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.items[*].action")).contains("auth.register", "auth.login", "auth.login_failed", "auth.password_changed");
		assertThat(body).doesNotContain("wrong-password").doesNotContain(TestApi.PASSWORD).doesNotContain("AnotherPass123");
		assertThat(JsonPath.<List<String>>read(body, "$.items[?(@.action=='auth.login_failed')].outcome")).containsExactly("FAILED");
		Session other = TestApi.register(mockMvc);
		mockMvc.perform(get("/api/me/audit-log").header("Authorization", other.bearer())).andExpect(jsonPath("$.items[?(@.action=='auth.login')]", hasSize(0)));
	}

	// ---- who can read it ----

	@Test
	void onlyOwnersAndAdminsReadTheWorkspaceLog() throws Exception {
		Fixture f = fixture();
		workspaceLog(f.admin(), f.workspace(), "").andExpect(status().isOk());
		workspaceLog(f.member(), f.workspace(), "").andExpect(status().isForbidden());
		workspaceLog(TestApi.register(mockMvc), f.workspace(), "").andExpect(status().isNotFound());
		// Entries of one workspace never show in another.
		Fixture other = fixture();
		workspaceLog(other.owner(), other.workspace(), "q=" + "Backend").andExpect(status().isOk());
		String body = workspaceLog(f.owner(), f.workspace(), "size=200").andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.items[*].targetId")).doesNotContain(other.project().toString());
	}

	@Test
	void projectManagersReadTheirProjectsLog() throws Exception {
		Fixture f = fixture();
		TestApi.addToProject(mockMvc, f.owner(), f.project(), f.member(), "DEVELOPER");
		mockMvc.perform(put("/api/projects/" + f.project() + "/status").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"));

		mockMvc.perform(get("/api/projects/" + f.project() + "/audit-log").header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.action=='project.status_change')]", hasSize(1)))
			.andExpect(jsonPath("$.items[?(@.action=='workspace.create')]", hasSize(0)));
		mockMvc.perform(get("/api/projects/" + f.project() + "/audit-log").header("Authorization", f.member().bearer())).andExpect(status().isForbidden());
	}

	@Test
	void theLogCanBeFilteredAndExported() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(put("/api/workspaces/" + f.workspace() + "/members/" + f.member().userId()).header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}")).andExpect(status().isNoContent());

		workspaceLog(f.owner(), f.workspace(), "actorId=" + f.owner().userId() + "&action=workspace.member_role_change")
			.andExpect(jsonPath("$.items", hasSize(1)));
		workspaceLog(f.owner(), f.workspace(), "actorId=" + UUID.randomUUID()).andExpect(jsonPath("$.items", hasSize(0)));
		workspaceLog(f.owner(), f.workspace(), "outcome=FAILED").andExpect(jsonPath("$.items", hasSize(0)));
		workspaceLog(f.owner(), f.workspace(), "from=2999-01-01T00:00:00Z").andExpect(jsonPath("$.items", hasSize(0)));

		byte[] csv = mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/audit-log/export?format=csv").header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
		assertThat(text).startsWith("﻿Thời gian,Người thực hiện,Hành động").contains("workspace.member_role_change", "role=ADMIN");
		mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/audit-log/export?format=xlsx").header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition",
					org.hamcrest.Matchers.containsString(".xlsx")));
		mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/audit-log/export?format=pdf").header("Authorization", f.owner().bearer()))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FORMAT"));
		// (The member was promoted to admin above, so a plain member is used for the refusal.)
		Session plain = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");
		mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/audit-log/export").header("Authorization", plain.bearer()))
			.andExpect(status().isForbidden());
		// Reading data out is itself recorded.
		workspaceLog(f.owner(), f.workspace(), "action=export.audit_log").andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].detail").value("format=xlsx"));
	}

	// ---- helpers ----

	private ResultActions workspaceLog(Session caller, UUID workspace, String query) throws Exception {
		return mockMvc.perform(get("/api/workspaces/" + workspace + "/audit-log" + (query.isEmpty() ? "" : "?" + query))
			.header("Authorization", caller.bearer()));
	}

	private static UUID created(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
	}

}
