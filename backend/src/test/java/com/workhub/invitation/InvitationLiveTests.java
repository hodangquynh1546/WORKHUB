package com.workhub.invitation;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Sending, accepting and revoking an invitation is pushed to the invitee and the workspace members. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class InvitationLiveTests {

	@LocalServerPort
	private int port;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void inviteeAndMembersHearAboutNewAcceptedAndRevokedInvitations() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Live invites");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		Session invitee = TestApi.register(mockMvc);
		Session stranger = TestApi.register(mockMvc);
		WsClient inviteeSocket = WsClient.connect(port).authenticate(invitee);
		WsClient memberSocket = WsClient.connect(port).authenticate(member);
		WsClient strangerSocket = WsClient.connect(port).authenticate(stranger);
		try {
			String created = mockMvc.perform(post("/api/workspaces/" + ws + "/invitations").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json("kind", "EMAIL", "email", invitee.email(), "role", "MEMBER")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
			inviteeSocket.next("invitations.changed");
			memberSocket.next("invitations.changed");

			String id = JsonPath.read(created, "$.invitation.id");
			mockMvc.perform(post("/api/me/invitations/" + id + "/accept").header("Authorization", invitee.bearer())).andExpect(status().isOk());
			memberSocket.next("invitations.changed");

			String second = mockMvc.perform(post("/api/workspaces/" + ws + "/invitations").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json("kind", "LINK", "role", "MEMBER")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
			memberSocket.next("invitations.changed");
			mockMvc.perform(delete("/api/invitations/" + JsonPath.<String>read(second, "$.invitation.id")).header("Authorization", owner.bearer()))
				.andExpect(status().isNoContent());
			memberSocket.next("invitations.changed");

			strangerSocket.expectNo("invitations.changed");
		}
		finally {
			inviteeSocket.close();
			memberSocket.close();
			strangerSocket.close();
		}
	}

}
