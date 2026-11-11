package com.workhub.realtime;

import static com.workhub.support.TestApi.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

/** Creating, renaming and deleting workspaces, teams and projects is pushed to the workspace's people. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class StructureLiveTests {

	@LocalServerPort
	private int port;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void everyStructuralWriteReachesTheWorkspacePeopleIncludingOnDelete() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Live structure");
		Session member = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		Session outsider = TestApi.register(mockMvc);
		WsClient memberSocket = WsClient.connect(port).authenticate(member);
		WsClient outsiderSocket = WsClient.connect(port).authenticate(outsider);
		try {
			UUID team = TestApi.createTeam(mockMvc, owner, ws, "Core");
			memberSocket.next("structure.changed");

			UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
			memberSocket.next("structure.changed");

			mockMvc.perform(put("/api/projects/" + project + "/status").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json("status", "ACTIVE"))).andExpect(status().isOk());
			memberSocket.next("structure.changed");

			mockMvc.perform(put("/api/teams/" + team).header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json("name", "Core renamed"))).andExpect(status().isOk());
			memberSocket.next("structure.changed");

			mockMvc.perform(put("/api/workspaces/" + ws).header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json("name", "Live structure 2"))).andExpect(status().isOk());
			memberSocket.next("structure.changed");

			// The member hears about the deletion although the membership rows are gone by the time it finishes.
			mockMvc.perform(delete("/api/workspaces/" + ws).header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json("name", "Live structure 2"))).andExpect(status().isNoContent());
			memberSocket.next("structure.changed");

			outsiderSocket.expectNo("structure.changed");
		}
		finally {
			memberSocket.close();
			outsiderSocket.close();
		}
	}

}
