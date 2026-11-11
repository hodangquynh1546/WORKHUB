package com.workhub.invitation;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.support.RecordingEmailSender;
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
class InvitationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecordingEmailSender emails;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void emailInvitationForSomeoneWithoutAnAccount() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		String email = TestApi.uniqueEmail();

		invite(owner, "/api/workspaces/" + ws + "/invitations", "EMAIL", email, "MEMBER")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.invitation.email").value(email))
			.andExpect(jsonPath("$.inviteUrl").isNotEmpty());
		String token = emails.lastToken(email, "/invitations/");

		// The invite page works before signing in.
		mockMvc.perform(get("/api/public/invitations/" + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workspaceName").value("Acme"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.invitedByName").value("Test User"));

		Session invitee = TestApi.register(mockMvc, email);
		accept(invitee, token).andExpect(status().isOk()).andExpect(jsonPath("$.workspaceId").value(ws.toString()));
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", invitee.bearer()))
			.andExpect(jsonPath("$.myRole").value("MEMBER"));

		// Single use.
		accept(invitee, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
		mockMvc.perform(get("/api/public/invitations/" + token)).andExpect(jsonPath("$.status").value("ACCEPTED"));
	}

	@Test
	void emailInvitationsOnlyWorkForTheAddressee() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		Session intended = TestApi.register(mockMvc);
		Session other = TestApi.register(mockMvc);
		invite(owner, "/api/workspaces/" + ws + "/invitations", "EMAIL", intended.email(), "MEMBER");
		String token = emails.lastToken(intended.email(), "/invitations/");

		accept(other, token).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INVITATION_EMAIL_MISMATCH"));
		mockMvc.perform(post("/api/invitations/decline").header("Authorization", intended.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("token", token))).andExpect(status().isNoContent());
		accept(intended, token).andExpect(status().isBadRequest());
	}

	@Test
	void linkInvitationsAreReusableUntilRevoked() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		String body = invite(owner, "/api/workspaces/" + ws + "/invitations", "LINK", null, "MEMBER")
			.andExpect(jsonPath("$.invitation.email").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(body, "$.inviteUrl");
		String token = url.substring(url.lastIndexOf('/') + 1);
		String id = JsonPath.read(body, "$.invitation.id");

		accept(TestApi.register(mockMvc), token).andExpect(status().isOk());
		accept(TestApi.register(mockMvc), token).andExpect(status().isOk());
		mockMvc.perform(get("/api/workspaces/" + ws + "/members").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(3));

		mockMvc.perform(delete("/api/invitations/" + id).header("Authorization", owner.bearer()))
			.andExpect(status().isNoContent());
		accept(TestApi.register(mockMvc), token).andExpect(status().isBadRequest());
	}

	@Test
	void teamInvitationAddsToWorkspaceAndTeam() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Mobile");
		Session invitee = TestApi.register(mockMvc);

		String token = TestApi.linkToken(mockMvc, owner, "/api/teams/" + team + "/invitations", "LEAD");
		accept(invitee, token).andExpect(jsonPath("$.teamId").value(team.toString()));

		// They are in the workspace only as a guest: they reach the team or project, not the workspace itself.
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", invitee.bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/teams/" + team).header("Authorization", invitee.bearer()))
			.andExpect(jsonPath("$.myRole").value("LEAD"));
	}

	@Test
	void projectInvitationAddsToWorkspaceAndProjectOnly() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Mobile");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session invitee = TestApi.register(mockMvc);

		String token = TestApi.linkToken(mockMvc, owner, "/api/projects/" + project + "/invitations", "QA_ENGINEER");
		accept(invitee, token).andExpect(jsonPath("$.projectId").value(project.toString()));

		// They are in the workspace only as a guest: they reach the team or project, not the workspace itself.
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", invitee.bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/projects/" + project).header("Authorization", invitee.bearer()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/teams/" + team).header("Authorization", invitee.bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/projects/" + project + "/invitations").header("Authorization", owner.bearer()))
			.andExpect(status().isOk());
		// A plain workspace member cannot invite into the project.
		mockMvc.perform(get("/api/projects/" + project + "/invitations").header("Authorization", invitee.bearer()))
			.andExpect(status().isForbidden());
	}

	@Test
	void invitingRequiresTheRightPermission() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		Session admin = TestApi.joinWorkspace(mockMvc, owner, ws, "ADMIN");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		String path = "/api/workspaces/" + ws + "/invitations";

		invite(member, path, "LINK", null, "MEMBER").andExpect(status().isForbidden());
		invite(admin, path, "LINK", null, "ADMIN").andExpect(status().isForbidden());
		invite(admin, path, "LINK", null, "MEMBER").andExpect(status().isCreated());
		invite(owner, path, "LINK", null, "OWNER").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ROLE"));
		invite(owner, path, "EMAIL", null, "MEMBER").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));
		invite(owner, path, "EMAIL", member.email(), "MEMBER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
		mockMvc.perform(get(path).header("Authorization", member.bearer())).andExpect(status().isForbidden());
	}

	@Test
	void invitationsShowUpInTheInviteesListAndCanBeAcceptedThere() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		Session invitee = TestApi.register(mockMvc);
		invite(owner, "/api/workspaces/" + ws + "/invitations", "EMAIL", invitee.email(), "ADMIN");

		String body = mockMvc.perform(get("/api/me/invitations").header("Authorization", invitee.bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].workspaceName").value("Acme"))
			.andExpect(jsonPath("$[0].role").value("ADMIN"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$[0].id");

		mockMvc.perform(post("/api/me/invitations/" + id + "/accept").header("Authorization", owner.bearer()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/me/invitations/" + id + "/accept").header("Authorization", invitee.bearer()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/workspaces/" + ws).header("Authorization", invitee.bearer()))
			.andExpect(jsonPath("$.myRole").value("ADMIN"));
		mockMvc.perform(get("/api/me/invitations").header("Authorization", invitee.bearer()))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void reinvitingReplacesTheEarlierLink() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		Session invitee = TestApi.register(mockMvc);
		String path = "/api/workspaces/" + ws + "/invitations";
		invite(owner, path, "EMAIL", invitee.email(), "MEMBER");
		String first = emails.lastToken(invitee.email(), "/invitations/");
		invite(owner, path, "EMAIL", invitee.email(), "MEMBER");
		String second = emails.lastToken(invitee.email(), "/invitations/");

		accept(invitee, first).andExpect(status().isBadRequest());
		accept(invitee, second).andExpect(status().isOk());
	}

	@Test
	void expiredInvitationsCannotBeUsed() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Acme");
		String token = TestApi.linkToken(mockMvc, owner, "/api/workspaces/" + ws + "/invitations", "MEMBER");
		jdbc.update("update invitations set expires_at = expires_at - interval '30' day where workspace_id = ?", ws);

		mockMvc.perform(get("/api/public/invitations/" + token)).andExpect(jsonPath("$.status").value("EXPIRED"));
		accept(TestApi.register(mockMvc), token).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
		mockMvc.perform(get("/api/workspaces/" + ws + "/invitations").header("Authorization", owner.bearer()))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unknownTokensAreNotFound() throws Exception {
		mockMvc.perform(get("/api/public/invitations/does-not-exist")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
	}

	private ResultActions invite(Session inviter, String path, String kind, String email, String role)
			throws Exception {
		return mockMvc.perform(post(path).header("Authorization", inviter.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("kind", kind, "email", email, "role", role)));
	}

	private ResultActions accept(Session invitee, String token) throws Exception {
		return mockMvc.perform(post("/api/invitations/accept").header("Authorization", invitee.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("token", token)));
	}

}
