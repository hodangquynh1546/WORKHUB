package com.workhub.user;

import static com.workhub.support.TestApi.PASSWORD;
import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.workhub.audit.AuditRetentionService;
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
class AccountTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuditRetentionService retention;

	private ResultActions transfer(UUID ws, Session caller, UUID newOwner, String password) throws Exception {
		return mockMvc.perform(post("/api/workspaces/" + ws + "/transfer-ownership").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("userId", newOwner.toString(), "password", password)));
	}

	private ResultActions deleteAccount(Session caller, String password) throws Exception {
		return mockMvc.perform(delete("/api/me").header("Authorization", caller.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(json("password", password)));
	}

	@Test
	void ownershipMovesToAnotherMemberAndTheOldOwnerBecomesAdmin() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Handover");
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session outsider = TestApi.register(mockMvc);

		transfer(ws, admin, admin.userId(), PASSWORD).andExpect(status().isForbidden());
		transfer(ws, outsider, admin.userId(), PASSWORD).andExpect(status().isNotFound());
		transfer(ws, owner, owner.userId(), PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ALREADY_OWNER"));
		transfer(ws, owner, outsider.userId(), PASSWORD).andExpect(status().isNotFound());
		transfer(ws, owner, admin.userId(), "wrong-password").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

		transfer(ws, owner, admin.userId(), PASSWORD).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", admin.bearer()))
			.andExpect(jsonPath("$.myRole").value("OWNER"));
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.myRole").value("ADMIN"))
			.andExpect(jsonPath("$.permissions[?(@ == 'DELETE_WORKSPACE')]").isEmpty());
		assertThat(jdbc.queryForObject("select count(*) from workspace_members where workspace_id = ? and role = 'OWNER'",
				Integer.class, ws)).isEqualTo(1);
		// The former owner can now leave.
		mockMvc.perform(delete("/api/workspaces/" + ws + "/members/" + owner.userId()).header("Authorization", owner.bearer()))
			.andExpect(status().isNoContent());
	}

	@Test
	void deletingAnAccountAnonymisesItAndFreesTheEmail() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Leaver");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "T");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session leaver = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, leaver, "DEVELOPER");
		mockMvc.perform(post("/api/projects/" + project + "/issues").header("Authorization", leaver.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("type", "TASK", "title", "Mine", "assigneeId", leaver.userId().toString())))
			.andExpect(status().isCreated());

		deleteAccount(leaver, "wrong-password").andExpect(status().isBadRequest());
		deleteAccount(leaver, PASSWORD).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, leaver.userId()))
			.isEqualTo("Người dùng đã xóa");
		assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, leaver.userId()))
			.endsWith("@deleted.invalid");
		assertThat(jdbc.queryForObject("select count(*) from workspace_members where user_id = ?", Integer.class, leaver.userId()))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from project_members where user_id = ?", Integer.class, leaver.userId()))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from issues where assignee_id = ?", Integer.class, leaver.userId()))
			.isZero();

		// The old credentials no longer work, and the address can be registered again.
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(json("email", leaver.email(), "password", PASSWORD))).andExpect(status().isUnauthorized());
		TestApi.register(mockMvc, leaver.email());
		// Nobody sees the deleted user in the workspace any more.
		mockMvc.perform(get("/api/workspaces/" + ws + "/members").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void aWorkspaceOwnerMustHandOverOrDeleteFirst() throws Exception {
		Session owner = TestApi.register(mockMvc);
		TestApi.createWorkspace(mockMvc, owner, "Mine");
		deleteAccount(owner, PASSWORD).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OWNS_WORKSPACES"));
	}

	@Test
	void retentionDeletesOnlyOldAuditEntries() throws Exception {
		Session user = TestApi.register(mockMvc);
		UUID old = UUID.randomUUID();
		UUID recent = UUID.randomUUID();
		jdbc.update("insert into audit_log (id, actor_id, actor_name, action, outcome, created_at) values (?, ?, 'x', 'test.retention', 'SUCCESS', ?)",
				old, user.userId(), Timestamp.from(Instant.now().minusSeconds(800L * 86400)));
		jdbc.update("insert into audit_log (id, actor_id, actor_name, action, outcome, created_at) values (?, ?, 'x', 'test.retention', 'SUCCESS', ?)",
				recent, user.userId(), Timestamp.from(Instant.now().minusSeconds(86400)));

		assertThat(retention.purge()).isGreaterThanOrEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where id = ?", Integer.class, old)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where id = ?", Integer.class, recent)).isEqualTo(1);
	}

}
