package com.workhub.chat;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ChatAttachmentTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, Session dev, Session outsider, UUID conversation) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Chat files");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "T");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session dev = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, dev, "DEVELOPER");
		Session outsider = TestApi.register(mockMvc);
		String body = mockMvc.perform(post("/api/chat/projects/" + project).header("Authorization", owner.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new Fixture(owner, dev, outsider, UUID.fromString(JsonPath.read(body, "$.id")));
	}

	private ResultActions upload(Session caller, UUID conversation, String name, String type, byte[] bytes, String caption) throws Exception {
		var request = multipart("/api/chat/conversations/" + conversation + "/attachments")
			.file(new MockMultipartFile("files", name, type, bytes));
		if (caption != null) {
			request.param("body", caption);
		}
		return mockMvc.perform(request.header("Authorization", caller.bearer()));
	}

	@Test
	void anImageIsSentWithACaptionAndShownInTheHistory() throws Exception {
		Fixture f = fixture();
		String body = upload(f.dev(), f.conversation(), "../../shot.png", "image/png", PNG, "Look at this").andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("Look at this"))
			.andExpect(jsonPath("$.attachments[0].fileName").value("shot.png"))
			.andExpect(jsonPath("$.attachments[0].image").value(true))
			.andExpect(jsonPath("$.attachments[0].size").value(PNG.length))
			.andReturn().getResponse().getContentAsString();
		String attachmentId = JsonPath.read(body, "$.attachments[0].id");

		mockMvc.perform(get("/api/chat/conversations/" + f.conversation() + "/messages").header("Authorization", f.owner().bearer()))
			.andExpect(jsonPath("$.items[0].attachments[0].id").value(attachmentId));

		byte[] downloaded = mockMvc.perform(get("/api/chat/attachments/" + attachmentId).header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/octet-stream"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(downloaded).isEqualTo(PNG);
	}

	@Test
	void aFileWithoutCaptionGetsOneAndAFakeImageIsNotTreatedAsAnImage() throws Exception {
		Fixture f = fixture();
		upload(f.owner(), f.conversation(), "notes.pdf", "application/pdf", "%PDF-1.4".getBytes(), null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("📎 notes.pdf"))
			.andExpect(jsonPath("$.attachments[0].image").value(false))
			.andExpect(jsonPath("$.attachments[0].contentType").value("application/octet-stream"));
		// Claims to be a PNG but is a script: kept as an opaque download, never offered as an image.
		upload(f.owner(), f.conversation(), "evil.png", "image/png", "<script>alert(1)</script>".getBytes(), null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.attachments[0].image").value(false));
	}

	@Test
	void emptyFilesAreRefusedAndOnlyConversationMembersCanSendOrDownload() throws Exception {
		Fixture f = fixture();
		upload(f.dev(), f.conversation(), "empty.txt", "text/plain", new byte[0], null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FILE_EMPTY"));
		upload(f.outsider(), f.conversation(), "x.png", "image/png", PNG, null).andExpect(status().isNotFound());

		String id = JsonPath.read(upload(f.dev(), f.conversation(), "a.png", "image/png", PNG, null).andReturn().getResponse().getContentAsString(),
				"$.attachments[0].id");
		mockMvc.perform(get("/api/chat/attachments/" + id).header("Authorization", f.outsider().bearer())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/chat/attachments/" + id)).andExpect(status().isUnauthorized());
	}

	@Test
	void deletingTheMessageHidesTheFile() throws Exception {
		Fixture f = fixture();
		String body = upload(f.dev(), f.conversation(), "a.png", "image/png", PNG, "temp").andReturn().getResponse().getContentAsString();
		String messageId = JsonPath.read(body, "$.id");
		String attachmentId = JsonPath.read(body, "$.attachments[0].id");

		mockMvc.perform(delete("/api/chat/messages/" + messageId).header("Authorization", f.dev().bearer())).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/chat/attachments/" + attachmentId).header("Authorization", f.dev().bearer())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/chat/conversations/" + f.conversation() + "/messages").header("Authorization", f.owner().bearer()))
			.andExpect(jsonPath("$.items[0].attachments").isEmpty());
	}

	@Test
	void plainTextMessagesStillWorkWithoutAttachment() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(post("/api/chat/conversations/" + f.conversation() + "/messages").header("Authorization", f.dev().bearer())
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json("body", "hello")))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.attachments").isEmpty());
	}

	@Test
	void severalFilesTravelInOneMessageInOrder() throws Exception {
		Fixture f = fixture();
		var request = multipart("/api/chat/conversations/" + f.conversation() + "/attachments")
			.file(new MockMultipartFile("files", "one.png", "image/png", PNG))
			.file(new MockMultipartFile("files", "two.txt", "text/plain", "two".getBytes()))
			.file(new MockMultipartFile("files", "three.pdf", "application/pdf", "%PDF".getBytes()));
		mockMvc.perform(request.header("Authorization", f.dev().bearer())).andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("📎 3 tệp"))
			.andExpect(jsonPath("$.attachments.length()").value(3))
			.andExpect(jsonPath("$.attachments[0].fileName").value("one.png"))
			.andExpect(jsonPath("$.attachments[0].image").value(true))
			.andExpect(jsonPath("$.attachments[1].fileName").value("two.txt"))
			.andExpect(jsonPath("$.attachments[2].fileName").value("three.pdf"));
		mockMvc.perform(get("/api/chat/conversations/" + f.conversation() + "/messages").header("Authorization", f.owner().bearer()))
			.andExpect(jsonPath("$.items[0].attachments.length()").value(3));
	}

	@Test
	void tooManyFilesAreRefused() throws Exception {
		Fixture f = fixture();
		var request = multipart("/api/chat/conversations/" + f.conversation() + "/attachments");
		for (int i = 0; i < 11; i++) {
			request.file(new MockMultipartFile("files", "f" + i + ".txt", "text/plain", "x".getBytes()));
		}
		mockMvc.perform(request.header("Authorization", f.dev().bearer())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FILE_COUNT"));
	}

	@Test
	void receiptsShowHowFarOthersHaveRead() throws Exception {
		Fixture f = fixture();
		mockMvc.perform(get("/api/chat/conversations/" + f.conversation() + "/receipts").header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(post("/api/chat/conversations/" + f.conversation() + "/messages").header("Authorization", f.owner().bearer())
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json("body", "anyone there?"))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/chat/conversations/" + f.conversation() + "/read").header("Authorization", f.dev().bearer())).andExpect(status().isOk());

		mockMvc.perform(get("/api/chat/conversations/" + f.conversation() + "/receipts").header("Authorization", f.owner().bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].userId").value(f.dev().userId().toString()))
			.andExpect(jsonPath("$[0].lastReadAt").isNotEmpty());
		mockMvc.perform(get("/api/chat/conversations/" + f.conversation() + "/receipts").header("Authorization", f.outsider().bearer()))
			.andExpect(status().isNotFound());
	}

}
