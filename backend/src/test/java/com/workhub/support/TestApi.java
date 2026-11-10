package com.workhub.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import com.workhub.auth.RefreshCookies;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Shared helpers for integration tests. Each test uses fresh random emails, so tests never collide. */
public final class TestApi {

	public static final String PASSWORD = "correct-horse-battery";

	private TestApi() {
	}

	public record Session(UUID userId, String email, String accessToken, Cookie refreshCookie) {

		public String bearer() {
			return "Bearer " + accessToken;
		}

	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	public static Session register(MockMvc mockMvc) throws Exception {
		return register(mockMvc, uniqueEmail());
	}

	public static Session register(MockMvc mockMvc, String email) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", PASSWORD, "fullName", "Test User")))
			.andExpect(status().isCreated())
			.andReturn();
		return session(result, email);
	}

	public static Session session(MvcResult result, String email) throws Exception {
		String body = result.getResponse().getContentAsString();
		return new Session(UUID.fromString(JsonPath.read(body, "$.user.id")), email,
				JsonPath.read(body, "$.accessToken"), result.getResponse().getCookie(RefreshCookies.NAME));
	}

	/** Creates a workspace owned by {@code owner}. */
	public static UUID createWorkspace(MockMvc mockMvc, Session owner, String name) throws Exception {
		return idOf(mockMvc
			.perform(post("/api/workspaces").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("name", name)))
			.andExpect(status().isCreated())
			.andReturn());
	}

	/** Creates a team in the workspace; {@code creator} needs MANAGE_TEAMS and becomes its lead. */
	public static UUID createTeam(MockMvc mockMvc, Session creator, UUID workspaceId, String name) throws Exception {
		return idOf(mockMvc
			.perform(post("/api/workspaces/" + workspaceId + "/teams").header("Authorization", creator.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("name", name)))
			.andExpect(status().isCreated())
			.andReturn());
	}

	/** Adds an existing workspace member to a team. */
	public static void addToTeam(MockMvc mockMvc, Session lead, UUID teamId, Session member, String role)
			throws Exception {
		mockMvc
			.perform(post("/api/teams/" + teamId + "/members").header("Authorization", lead.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("userId", member.userId().toString(), "role", role)))
			.andExpect(status().isNoContent());
	}

	/** Creates a Scrum project in the team; the caller becomes its project manager. */
	public static UUID createProject(MockMvc mockMvc, Session creator, UUID teamId, String key) throws Exception {
		return idOf(mockMvc
			.perform(post("/api/teams/" + teamId + "/projects").header("Authorization", creator.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("key", key, "name", "Project " + key, "methodology", "SCRUM")))
			.andExpect(status().isCreated())
			.andReturn());
	}

	/** Adds an existing workspace member to a project. */
	public static void addToProject(MockMvc mockMvc, Session manager, UUID projectId, Session member, String role)
			throws Exception {
		mockMvc
			.perform(post("/api/projects/" + projectId + "/members").header("Authorization", manager.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("userId", member.userId().toString(), "role", role)))
			.andExpect(status().isNoContent());
	}

	/** A short unique project key such as {@code PKA1B2C3}. */
	public static String uniqueKey() {
		return "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
	}

	/** Registers a new user and brings them into the workspace with {@code role} through a link invitation. */
	public static Session joinWorkspace(MockMvc mockMvc, Session inviter, UUID workspaceId, String role)
			throws Exception {
		Session member = register(mockMvc);
		String token = linkToken(mockMvc, inviter, "/api/workspaces/" + workspaceId + "/invitations", role);
		accept(mockMvc, member, token);
		return member;
	}

	/** Creates a LINK invitation at {@code invitationsPath} and returns its raw token. */
	public static String linkToken(MockMvc mockMvc, Session inviter, String invitationsPath, String role)
			throws Exception {
		String body = mockMvc
			.perform(post(invitationsPath).header("Authorization", inviter.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("kind", "LINK", "role", role)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String url = JsonPath.read(body, "$.inviteUrl");
		return url.substring(url.lastIndexOf('/') + 1);
	}

	public static void accept(MockMvc mockMvc, Session invitee, String token) throws Exception {
		mockMvc
			.perform(post("/api/invitations/accept").header("Authorization", invitee.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("token", token)))
			.andExpect(status().isOk());
	}

	private static UUID idOf(MvcResult result) throws Exception {
		return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
	}

	/** Builds a flat JSON object from key/value pairs; values are strings or null. */
	public static String json(String... keyValues) {
		StringBuilder json = new StringBuilder("{");
		for (int i = 0; i < keyValues.length; i += 2) {
			if (i > 0) {
				json.append(',');
			}
			json.append('"').append(keyValues[i]).append("\":");
			String value = keyValues[i + 1];
			json.append((value == null) ? "null" : '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"');
		}
		return json.append('}').toString();
	}

}
