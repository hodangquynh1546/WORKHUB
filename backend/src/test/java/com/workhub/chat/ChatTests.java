package com.workhub.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
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
class ChatTests {

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, Session dev, Session other,
			Session loner) {
	}

	/** Owner leads the team and the project; dev and other are on both; loner is only in the workspace. */
	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session dev = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		Session other = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		for (Session s : List.of(dev, other)) {
			TestApi.addToTeam(mockMvc, owner, team, s, "MEMBER");
			TestApi.addToProject(mockMvc, owner, project, s, "DEVELOPER");
		}
		Session loner = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		return new Fixture(owner, ws, team, project, dev, other, loner);
	}

	// ---- project and team channels ----

	@Test
	void projectAndTeamChannelsFollowMembership() throws Exception {
		Fixture f = fixture();

		String body = conversations(f.dev()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$[?(@.kind=='PROJECT')].projectId")).containsExactly(f.project().toString());
		assertThat(JsonPath.<List<String>>read(body, "$[?(@.kind=='TEAM')].teamId")).containsExactly(f.team().toString());
		// Someone who is only in the workspace has no channels.
		conversations(f.loner()).andExpect(jsonPath("$.length()").value(0));

		String project = JsonPath.<List<String>>read(body, "$[?(@.kind=='PROJECT')].id").get(0);
		send(f.dev(), project, "Hello team", null, List.of()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("Hello team")).andExpect(jsonPath("$.sender.id").value(f.dev().userId().toString()));
		messages(f.other(), project, "").andExpect(jsonPath("$.items.length()").value(1));
		messages(f.loner(), project, "").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
		send(f.loner(), project, "let me in", null, List.of()).andExpect(status().isNotFound());
	}

	@Test
	void unreadCountsTrackWhatOthersSaidSinceYouLastRead() throws Exception {
		Fixture f = fixture();
		String project = projectChannel(f, f.dev());
		projectChannel(f, f.other());

		send(f.dev(), project, "one", null, List.of());
		send(f.dev(), project, "two", null, List.of());

		conversations(f.other()).andExpect(jsonPath("$[?(@.id=='" + project + "')].unread").value(2))
			.andExpect(jsonPath("$[?(@.id=='" + project + "')].last.snippet").value("two"));
		// Your own messages never count against you.
		conversations(f.dev()).andExpect(jsonPath("$[?(@.id=='" + project + "')].unread").value(0));
		mockMvc.perform(get("/api/chat/unread-count").header("Authorization", f.other().bearer())).andExpect(jsonPath("$.unread").value(2));

		// Answering means you have read what came before.
		send(f.other(), project, "three", null, List.of());
		conversations(f.other()).andExpect(jsonPath("$[?(@.id=='" + project + "')].unread").value(0));
		conversations(f.dev()).andExpect(jsonPath("$[?(@.id=='" + project + "')].unread").value(1));

		mockMvc.perform(post("/api/chat/conversations/" + project + "/read").header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(0));
		conversations(f.dev()).andExpect(jsonPath("$[?(@.id=='" + project + "')].unread").value(0));
		send(f.other(), project, "four", null, List.of());
		conversations(f.dev()).andExpect(jsonPath("$[?(@.id=='" + project + "')].unread").value(1));
	}

	@Test
	void openingAProjectChannelNeedsAccessToTheProject() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(post("/api/chat/projects/" + f.project()).header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("PROJECT"));
		mockMvc.perform(post("/api/chat/projects/" + f.project()).header("Authorization", f.loner().bearer()))
			.andExpect(status().isNotFound());
	}

	// ---- direct messages and groups ----

	@Test
	void directConversationsAreUniquePerPairAndStayInsideAWorkspace() throws Exception {
		Fixture f = fixture();
		String a = openDirect(f.dev(), f.workspace(), f.other()).andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("DIRECT")).andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.notNullValue()))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(a, "$.id");
		// The same pair, from the other side, is the same conversation.
		openDirect(f.other(), f.workspace(), f.dev()).andExpect(jsonPath("$.id").value(id));
		openDirect(f.dev(), f.workspace(), f.dev()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_CHAT_WITH_SELF"));
		Session stranger = TestApi.register(mockMvc);
		openDirect(f.dev(), f.workspace(), stranger).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_IN_WORKSPACE"));
		openDirect(stranger, f.workspace(), f.dev()).andExpect(status().isNotFound());

		send(f.dev(), id, "psst", null, List.of());
		conversations(f.other()).andExpect(jsonPath("$[?(@.id=='" + id + "')].unread").value(1))
			.andExpect(jsonPath("$[?(@.id=='" + id + "')].title").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
		messages(f.loner(), id, "").andExpect(status().isNotFound());
	}

	@Test
	void groupsHaveTheirOwnMembers() throws Exception {
		Fixture f = fixture();
		String group = JsonPath.read(createGroup(f.dev(), f.workspace(), "Lunch", List.of(f.other().userId()))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.memberCount").value(2)).andReturn().getResponse().getContentAsString(), "$.id");

		send(f.other(), group, "where?", null, List.of()).andExpect(status().isCreated());
		messages(f.loner(), group, "").andExpect(status().isNotFound());

		mockMvc.perform(post("/api/chat/groups/" + group + "/members").header("Authorization", f.loner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"memberIds\":[\"" + f.loner().userId() + "\"]}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/chat/groups/" + group + "/members").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"memberIds\":[\"" + f.loner().userId() + "\"]}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.memberCount").value(3));
		messages(f.loner(), group, "").andExpect(status().isOk());
		// Someone who joins later only counts what is said afterwards.
		conversations(f.loner()).andExpect(jsonPath("$[?(@.id=='" + group + "')].unread").value(0));

		mockMvc.perform(put("/api/chat/groups/" + group).header("Authorization", f.other().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Dinner\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Dinner"));
		mockMvc.perform(post("/api/chat/groups/" + group + "/leave").header("Authorization", f.loner().bearer())).andExpect(status().isNoContent());
		messages(f.loner(), group, "").andExpect(status().isNotFound());

		Session stranger = TestApi.register(mockMvc);
		createGroup(f.dev(), f.workspace(), "Bad", List.of(stranger.userId())).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_NOT_IN_WORKSPACE"));
		// Direct conversations are not groups.
		String direct = JsonPath.read(openDirect(f.dev(), f.workspace(), f.other()).andReturn().getResponse().getContentAsString(), "$.id");
		mockMvc.perform(post("/api/chat/groups/" + direct + "/leave").header("Authorization", f.dev().bearer())).andExpect(status().isBadRequest());
	}

	// ---- messages ----

	@Test
	void messagesCanBeRepliedToEditedDeletedAndReactedTo() throws Exception {
		Fixture f = fixture();
		String project = projectChannel(f, f.dev());
		String first = idOf(send(f.dev(), project, "original", null, List.of()));

		String reply = idOf(send(f.other(), project, "answer", first, List.of()));
		messages(f.dev(), project, "").andExpect(jsonPath("$.items[1].replyTo.snippet").value("original"));
		String elsewhere = JsonPath.read(openDirect(f.dev(), f.workspace(), f.other()).andReturn().getResponse().getContentAsString(), "$.id");
		send(f.dev(), elsewhere, "x", first, List.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REPLY"));

		edit(f.other(), first, "hijacked").andExpect(status().isForbidden());
		edit(f.dev(), first, "edited").andExpect(status().isOk()).andExpect(jsonPath("$.body").value("edited"))
			.andExpect(jsonPath("$.editedAt").isNotEmpty());

		react(f.other(), first, "👍").andExpect(jsonPath("$.reactions[0].emoji").value("👍")).andExpect(jsonPath("$.reactions[0].userIds", hasSize(1)));
		react(f.dev(), first, "👍").andExpect(jsonPath("$.reactions[0].userIds", hasSize(2)));
		react(f.other(), first, "👍").andExpect(jsonPath("$.reactions[0].userIds", hasSize(1)));
		react(f.dev(), first, "👍").andExpect(jsonPath("$.reactions", hasSize(0)));
		react(f.loner(), first, "👍").andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/chat/messages/" + first).header("Authorization", f.other().bearer())).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/chat/messages/" + first).header("Authorization", f.dev().bearer())).andExpect(status().isNoContent());
		// The text is gone, the place in the conversation (and the reply that points at it) stays.
		messages(f.other(), project, "").andExpect(jsonPath("$.items[0].deleted").value(true)).andExpect(jsonPath("$.items[0].body").doesNotExist())
			.andExpect(jsonPath("$.items[1].replyTo.deleted").value(true));
		edit(f.dev(), first, "again").andExpect(status().isNotFound());
		assertThat(reply).isNotBlank();
	}

	@Test
	void historyIsPagedBackwards() throws Exception {
		Fixture f = fixture();
		String project = projectChannel(f, f.dev());
		for (int i = 1; i <= 5; i++) {
			send(f.dev(), project, "m" + i, null, List.of());
			Thread.sleep(5);
		}

		String page = messages(f.dev(), project, "size=2").andExpect(jsonPath("$.hasMore").value(true))
			.andExpect(jsonPath("$.items[0].body").value("m4")).andExpect(jsonPath("$.items[1].body").value("m5"))
			.andReturn().getResponse().getContentAsString();
		String before = JsonPath.read(page, "$.items[0].createdAt");
		messages(f.dev(), project, "size=2&before=" + before).andExpect(jsonPath("$.items[0].body").value("m2"))
			.andExpect(jsonPath("$.items[1].body").value("m3")).andExpect(jsonPath("$.hasMore").value(true));
		messages(f.dev(), project, "size=10").andExpect(jsonPath("$.items", hasSize(5))).andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void mentionsNotifyOnlyPeopleInTheConversation() throws Exception {
		Fixture f = fixture();
		String project = projectChannel(f, f.dev());
		mockMvc.perform(post("/api/notifications/read-all").header("Authorization", f.other().bearer()));

		send(f.dev(), project, "@other look", null, List.of(f.other().userId())).andExpect(status().isCreated())
			.andExpect(jsonPath("$.mentions[0]").value(f.other().userId().toString()));
		mockMvc.perform(get("/api/notifications?unread=true").header("Authorization", f.other().bearer()))
			.andExpect(jsonPath("$.items[0].type").value("MENTIONED")).andExpect(jsonPath("$.items[0].link").value("/chat/" + project));
		send(f.dev(), project, "@loner", null, List.of(f.loner().userId())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MENTION_NOT_MEMBER"));
		mockMvc.perform(get("/api/chat/conversations/" + project + "/members").header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + f.owner().userId() + "')]").isNotEmpty())
			.andExpect(jsonPath("$[?(@.id=='" + f.loner().userId() + "')]").isEmpty());
	}

	@Test
	void searchOnlyReachesConversationsYouAreIn() throws Exception {
		Fixture f = fixture();
		String project = projectChannel(f, f.dev());
		send(f.dev(), project, "The Deployment is at 100% today", null, List.of());
		String direct = JsonPath.read(openDirect(f.dev(), f.workspace(), f.other()).andReturn().getResponse().getContentAsString(), "$.id");
		send(f.dev(), direct, "deployment secret between us", null, List.of());

		search(f.dev(), "deployment").andExpect(jsonPath("$", hasSize(2)));
		search(f.other(), "deployment").andExpect(jsonPath("$", hasSize(2)));
		// The direct conversation is invisible to everybody else.
		search(f.owner(), "deployment").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].conversationTitle").value(org.hamcrest.Matchers.notNullValue()));
		search(f.loner(), "deployment").andExpect(jsonPath("$", hasSize(0)));
		search(f.dev(), "100%").andExpect(jsonPath("$", hasSize(1)));
		search(f.dev(), "5%").andExpect(jsonPath("$", hasSize(0)));
		search(f.dev(), " ").andExpect(jsonPath("$", hasSize(0)));
	}

	// ---- helpers ----

	/** Lists the caller's conversations (which creates the project/team channels) and returns the project channel id. */
	private String projectChannel(Fixture f, Session caller) throws Exception {
		return JsonPath.<List<String>>read(conversations(caller).andReturn().getResponse().getContentAsString(), "$[?(@.kind=='PROJECT')].id").get(0);
	}

	private ResultActions conversations(Session caller) throws Exception {
		return mockMvc.perform(get("/api/chat/conversations").header("Authorization", caller.bearer()));
	}

	private ResultActions messages(Session caller, String conversation, String query) throws Exception {
		return mockMvc.perform(get("/api/chat/conversations/" + conversation + "/messages" + (query.isEmpty() ? "" : "?" + query))
			.header("Authorization", caller.bearer()));
	}

	private ResultActions send(Session caller, String conversation, String text, String replyTo, List<UUID> mentions) throws Exception {
		String ids = mentions.stream().map((id) -> "\"" + id + "\"").collect(Collectors.joining(","));
		return mockMvc.perform(post("/api/chat/conversations/" + conversation + "/messages").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"" + text + "\"" + ((replyTo != null) ? ",\"replyToId\":\"" + replyTo + "\"" : "") + ",\"mentionIds\":[" + ids + "]}"));
	}

	private ResultActions edit(Session caller, String message, String text) throws Exception {
		return mockMvc.perform(put("/api/chat/messages/" + message).header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + text + "\"}"));
	}

	private ResultActions react(Session caller, String message, String emoji) throws Exception {
		return mockMvc.perform(post("/api/chat/messages/" + message + "/reactions").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"" + emoji + "\"}"));
	}

	private ResultActions openDirect(Session caller, UUID workspace, Session other) throws Exception {
		return openDirect(caller, workspace, other.userId());
	}

	private ResultActions openDirect(Session caller, UUID workspace, UUID other) throws Exception {
		return mockMvc.perform(post("/api/chat/direct").header("Authorization", caller.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"workspaceId\":\"" + workspace + "\",\"userId\":\"" + other + "\"}"));
	}

	private ResultActions createGroup(Session caller, UUID workspace, String title, List<UUID> members) throws Exception {
		String ids = members.stream().map((id) -> "\"" + id + "\"").collect(Collectors.joining(","));
		return mockMvc.perform(post("/api/chat/groups").header("Authorization", caller.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"workspaceId\":\"" + workspace + "\",\"title\":\"" + title + "\",\"memberIds\":[" + ids + "]}"));
	}

	private ResultActions search(Session caller, String q) throws Exception {
		return mockMvc.perform(get("/api/chat/search").param("q", q).header("Authorization", caller.bearer()));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

}
