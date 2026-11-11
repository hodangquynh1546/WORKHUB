package com.workhub.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;
import com.workhub.support.WsClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Typing indicators and seen receipts travel over the WebSocket. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChatLiveTests {

	@LocalServerPort
	private int port;

	@Autowired
	private MockMvc mockMvc;

	private record Room(Session owner, Session dev, Session outsider, UUID conversation) {
	}

	private Room room() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Live chat");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "T");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session dev = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, dev, "DEVELOPER");
		Session outsider = TestApi.register(mockMvc);
		String body = mockMvc.perform(post("/api/chat/projects/" + project).header("Authorization", owner.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new Room(owner, dev, outsider, UUID.fromString(JsonPath.read(body, "$.id")));
	}

	@Test
	void typingReachesTheOthersButNotTheTypistOrOutsiders() throws Exception {
		Room r = room();
		WsClient owner = WsClient.connect(port).authenticate(r.owner());
		WsClient dev = WsClient.connect(port).authenticate(r.dev());
		WsClient outsider = WsClient.connect(port).authenticate(r.outsider());
		try {
			dev.send("{\"type\":\"chat.typing\",\"conversationId\":\"" + r.conversation() + "\"}");
			String frame = owner.next("chat.typing");
			assertThat(JsonPath.<String>read(frame, "$.userId")).isEqualTo(r.dev().userId().toString());
			assertThat(JsonPath.<String>read(frame, "$.conversationId")).isEqualTo(r.conversation().toString());
			assertThat(JsonPath.<String>read(frame, "$.name")).isNotBlank();
			dev.expectNo("chat.typing");
			outsider.expectNo("chat.typing");

			// An outsider typing into a conversation they cannot see is ignored, and bursts are throttled.
			outsider.send("{\"type\":\"chat.typing\",\"conversationId\":\"" + r.conversation() + "\"}");
			dev.send("{\"type\":\"chat.typing\",\"conversationId\":\"" + r.conversation() + "\"}");
			owner.expectNo("chat.typing");
		}
		finally {
			owner.close();
			dev.close();
			outsider.close();
		}
	}

	@Test
	void readingTellsTheOthersWhoSawTheConversation() throws Exception {
		Room r = room();
		WsClient owner = WsClient.connect(port).authenticate(r.owner());
		try {
			mockMvc.perform(post("/api/chat/conversations/" + r.conversation() + "/read").header("Authorization", r.dev().bearer()))
				.andExpect(status().isOk());
			String frame = owner.next("chat.seen");
			assertThat(JsonPath.<String>read(frame, "$.userId")).isEqualTo(r.dev().userId().toString());
			assertThat(JsonPath.<String>read(frame, "$.at")).isNotBlank();
		}
		finally {
			owner.close();
		}
	}

}
