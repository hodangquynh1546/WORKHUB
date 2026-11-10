package com.workhub.collab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The relay and storage side of collaborative editing. The server never reads Yjs data, so these tests use
 * arbitrary bytes; real Yjs documents are exercised in the browser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CollabTests {

	@LocalServerPort
	private int port;

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID project, Session alice, Session bob, Session viewer, UUID doc) {
	}

	private Fixture fixture(String content) throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session alice = member(owner, ws, project, "DEVELOPER");
		Session bob = member(owner, ws, project, "DEVELOPER");
		Session viewer = member(owner, ws, project, "STAKEHOLDER");
		UUID doc = UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + project + "/documents")
			.header("Authorization", alice.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Spec\",\"type\":\"SRS\",\"content\":\"" + content + "\"}")).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id"));
		return new Fixture(owner, project, alice, bob, viewer, doc);
	}

	private Session member(Session owner, UUID ws, UUID project, String role) throws Exception {
		Session s = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, s, role);
		return s;
	}

	private static String b64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes());
	}

	private String join(WsClient client, UUID doc) throws Exception {
		client.send("{\"type\":\"doc.join\",\"documentId\":\"" + doc + "\"}");
		return client.next("doc.joined");
	}

	private void update(WsClient client, UUID doc, int epoch, String data) throws Exception {
		client.send("{\"type\":\"doc.update\",\"documentId\":\"" + doc + "\",\"epoch\":" + epoch + ",\"update\":\"" + b64(data) + "\"}");
	}

	private ResultActions state(Session who, UUID doc) throws Exception {
		return mockMvc.perform(get("/api/documents/" + doc + "/collab").header("Authorization", who.bearer()));
	}

	private ResultActions snapshot(Session who, UUID doc, int epoch, long seq, String data, String text, boolean createVersion, String note) throws Exception {
		return mockMvc.perform(post("/api/documents/" + doc + "/collab/snapshot").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"epoch\":" + epoch + ",\"seq\":" + seq + ",\"state\":\"" + b64(data) + "\",\"text\":\"" + text + "\",\"createVersion\":" + createVersion
					+ ((note != null) ? ",\"note\":\"" + note + "\"" : "") + "}"));
	}

	// ---- joining and relaying ----

	@Test
	void joiningNeedsAccessAndTellsWhoMayEdit() throws Exception {
		Fixture f = fixture("hello");
		Session outsider = TestApi.register(mockMvc);
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		WsClient viewer = WsClient.connect(port).authenticate(f.viewer());
		WsClient stranger = WsClient.connect(port).authenticate(outsider);

		String joined = join(alice, f.doc());
		assertThat(JsonPath.<Boolean>read(joined, "$.canEdit")).isTrue();
		assertThat(JsonPath.<Integer>read(joined, "$.epoch")).isEqualTo(1);
		assertThat(JsonPath.<Boolean>read(join(viewer, f.doc()), "$.canEdit")).isFalse();
		stranger.send("{\"type\":\"doc.join\",\"documentId\":\"" + f.doc() + "\"}");
		assertThat(JsonPath.<String>read(stranger.next("doc.error"), "$.code")).isEqualTo("DOCUMENT_NOT_FOUND");
		alice.close();
		viewer.close();
		stranger.close();
	}

	@Test
	void updatesAreStoredNumberedAndRelayedToTheOthers() throws Exception {
		Fixture f = fixture("hello");
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		WsClient bob = WsClient.connect(port).authenticate(f.bob());
		WsClient viewer = WsClient.connect(port).authenticate(f.viewer());
		join(alice, f.doc());
		join(bob, f.doc());
		join(viewer, f.doc());

		update(alice, f.doc(), 1, "first");
		long seq1 = JsonPath.<Number>read(alice.next("doc.ack"), "$.seq").longValue();
		String relayed = bob.next("doc.update");
		assertThat(JsonPath.<String>read(relayed, "$.update")).isEqualTo(b64("first"));
		assertThat(JsonPath.<Number>read(relayed, "$.seq").longValue()).isEqualTo(seq1);
		viewer.next("doc.update"); // viewers watch too
		update(bob, f.doc(), 1, "second");
		long seq2 = JsonPath.<Number>read(bob.next("doc.ack"), "$.seq").longValue();
		assertThat(seq2).isGreaterThan(seq1);
		assertThat(JsonPath.<String>read(alice.next("doc.update"), "$.update")).isEqualTo(b64("second"));
		alice.expectNo("doc.update"); // never echoed back to the sender

		// A late joiner catches up from the stored log, in order.
		state(f.viewer(), f.doc()).andExpect(status().isOk())
			.andExpect(jsonPath("$.empty").value(false))
			.andExpect(jsonPath("$.updates", hasSize(2)))
			.andExpect(jsonPath("$.updates[0].update").value(b64("first")))
			.andExpect(jsonPath("$.updates[1].update").value(b64("second")))
			.andExpect(jsonPath("$.lastSeq").value(seq2))
			.andExpect(jsonPath("$.canEdit").value(false));
		alice.close();
		bob.close();
		viewer.close();
	}

	@Test
	void viewersAndOutsidersCannotWrite() throws Exception {
		Fixture f = fixture("hello");
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		WsClient viewer = WsClient.connect(port).authenticate(f.viewer());
		join(alice, f.doc());
		join(viewer, f.doc());

		update(viewer, f.doc(), 1, "vandalism");
		assertThat(JsonPath.<String>read(viewer.next("doc.error"), "$.code")).isEqualTo("FORBIDDEN");
		alice.expectNo("doc.update");
		// Not even in the room.
		WsClient bob = WsClient.connect(port).authenticate(f.bob());
		update(bob, f.doc(), 1, "sneaky");
		assertThat(JsonPath.<String>read(bob.next("doc.error"), "$.code")).isEqualTo("FORBIDDEN");
		state(f.alice(), f.doc()).andExpect(jsonPath("$.empty").value(true));
		alice.close();
		viewer.close();
		bob.close();
	}

	@Test
	void awarenessIsRelayedRememberedForLateJoinersAndClearedWhenSomeoneLeaves() throws Exception {
		Fixture f = fixture("hello");
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		WsClient bob = WsClient.connect(port).authenticate(f.bob());
		join(alice, f.doc());
		join(bob, f.doc());

		alice.send("{\"type\":\"doc.awareness\",\"documentId\":\"" + f.doc() + "\",\"clientId\":4242,\"update\":\"" + b64("cursor-at-5") + "\"}");
		assertThat(JsonPath.<String>read(bob.next("doc.awareness"), "$.update")).isEqualTo(b64("cursor-at-5"));
		alice.expectNo("doc.awareness");

		WsClient viewer = WsClient.connect(port).authenticate(f.viewer());
		join(viewer, f.doc());
		assertThat(JsonPath.<String>read(viewer.next("doc.awareness"), "$.update")).isEqualTo(b64("cursor-at-5"));

		alice.close();
		String left = bob.next("doc.peer_left");
		assertThat(JsonPath.<Number>read(left, "$.clientIds[0]").longValue()).isEqualTo(4242L);
		bob.close();
		viewer.close();
	}

	// ---- seeding an empty CRDT from the stored text ----

	@Test
	void onlyOneEditorSeedsTheDocumentAndTheJobMovesIfTheyLeave() throws Exception {
		Fixture f = fixture("existing text");
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		WsClient bob = WsClient.connect(port).authenticate(f.bob());
		WsClient viewer = WsClient.connect(port).authenticate(f.viewer());

		assertThat(JsonPath.<Boolean>read(join(alice, f.doc()), "$.init")).isTrue();
		assertThat(JsonPath.<Boolean>read(join(bob, f.doc()), "$.init")).isFalse();
		assertThat(JsonPath.<Boolean>read(join(viewer, f.doc()), "$.init")).isFalse();

		// The seeder disappears before doing it: the next editor is asked (never the viewer).
		alice.close();
		bob.next("doc.init");
		update(bob, f.doc(), 1, "seeded");
		bob.next("doc.ack");
		// Once there is data, nobody is asked to seed again.
		WsClient late = WsClient.connect(port).authenticate(f.alice());
		assertThat(JsonPath.<Boolean>read(join(late, f.doc()), "$.init")).isFalse();
		bob.close();
		viewer.close();
		late.close();
	}

	@Test
	void anEmptyDocumentNeedsNoSeeding() throws Exception {
		Fixture f = fixture("");
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		assertThat(JsonPath.<Boolean>read(join(alice, f.doc()), "$.init")).isFalse();
		alice.close();
	}

	// ---- snapshots, versions and compaction ----

	@Test
	void snapshotsCompactTheLogAndRefreshTheTextButOnlyForwards() throws Exception {
		Fixture f = fixture("old");
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		join(alice, f.doc());
		update(alice, f.doc(), 1, "u1");
		long s1 = JsonPath.<Number>read(alice.next("doc.ack"), "$.seq").longValue();
		update(alice, f.doc(), 1, "u2");
		long s2 = JsonPath.<Number>read(alice.next("doc.ack"), "$.seq").longValue();
		update(alice, f.doc(), 1, "u3");
		long s3 = JsonPath.<Number>read(alice.next("doc.ack"), "$.seq").longValue();

		snapshot(f.alice(), f.doc(), 1, s2, "state-after-u2", "text after two edits", false, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.applied").value(true));
		// Updates up to the snapshot are gone; the later one remains.
		state(f.bob(), f.doc()).andExpect(jsonPath("$.snapshot").value(b64("state-after-u2")))
			.andExpect(jsonPath("$.snapshotSeq").value(s2)).andExpect(jsonPath("$.updates", hasSize(1)))
			.andExpect(jsonPath("$.updates[0].seq").value(s3));
		// The plain text (search, lists, export) follows, without a new version so soon after the last one.
		mockMvc.perform(get("/api/documents/" + f.doc()).header("Authorization", f.bob().bearer()))
			.andExpect(jsonPath("$.content").value("text after two edits")).andExpect(jsonPath("$.version").value(1));

		// A client that is behind (it has only seen u1) must not drag the stored text back.
		snapshot(f.bob(), f.doc(), 1, s1, "state-after-u1", "stale text", false, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.applied").value(false));
		mockMvc.perform(get("/api/documents/" + f.doc()).header("Authorization", f.bob().bearer()))
			.andExpect(jsonPath("$.content").value("text after two edits"));
		state(f.bob(), f.doc()).andExpect(jsonPath("$.snapshot").value(b64("state-after-u2")));
		alice.close();
	}

	@Test
	void versionsAreAddedOnRequestAndNotForUnchangedText() throws Exception {
		Fixture f = fixture("v1 text");
		snapshot(f.alice(), f.doc(), 1, 5, "s", "draft one", false, null).andExpect(jsonPath("$.version").value(1));
		snapshot(f.alice(), f.doc(), 1, 6, "s", "milestone text", true, "Chốt bản đầu").andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(2));
		// Asking again for the same text adds nothing.
		snapshot(f.bob(), f.doc(), 1, 7, "s", "milestone text", true, "again").andExpect(jsonPath("$.version").value(2));

		mockMvc.perform(get("/api/documents/" + f.doc() + "/versions").header("Authorization", f.bob().bearer()))
			.andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].note").value("Chốt bản đầu"))
			.andExpect(jsonPath("$[0].author.id").value(f.alice().userId().toString()));
		mockMvc.perform(get("/api/documents/" + f.doc() + "/versions/2").header("Authorization", f.bob().bearer()))
			.andExpect(jsonPath("$.content").value("milestone text"));
		snapshot(f.viewer(), f.doc(), 1, 8, "s", "x", false, null).andExpect(status().isForbidden());
		snapshot(f.alice(), f.doc(), 1, 9, "s", "x".repeat(100_001), false, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
	}

	// ---- replacing the text from outside ----

	@Test
	void restoringOrSavingPlainTextResetsEveryOpenEditor() throws Exception {
		Fixture f = fixture("original");
		WsClient alice = WsClient.connect(port).authenticate(f.alice());
		WsClient bob = WsClient.connect(port).authenticate(f.bob());
		join(alice, f.doc());
		join(bob, f.doc());
		update(alice, f.doc(), 1, "live edit");
		alice.next("doc.ack");

		// A plain save from the API replaces the text: live editors must reload.
		mockMvc.perform(put("/api/documents/" + f.doc()).header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Spec\",\"type\":\"SRS\",\"content\":\"replaced from outside\",\"baseVersion\":1}")).andExpect(status().isOk());
		assertThat(JsonPath.<Integer>read(alice.next("doc.reset"), "$.epoch")).isEqualTo(2);
		assertThat(JsonPath.<Integer>read(bob.next("doc.reset"), "$.epoch")).isEqualTo(2);
		state(f.bob(), f.doc()).andExpect(jsonPath("$.epoch").value(2)).andExpect(jsonPath("$.empty").value(true))
			.andExpect(jsonPath("$.content").value("replaced from outside"));

		// An editor that missed the news and still talks about epoch 1 is told to reload; nothing is stored.
		update(alice, f.doc(), 1, "stale edit");
		alice.next("doc.reset");
		state(f.bob(), f.doc()).andExpect(jsonPath("$.updates", hasSize(0)));
		snapshot(f.alice(), f.doc(), 1, 99, "s", "from the old epoch", false, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_EPOCH"));

		// Restoring an old version does the same.
		mockMvc.perform(post("/api/documents/" + f.doc() + "/versions/1/restore").header("Authorization", f.owner().bearer())).andExpect(status().isOk());
		assertThat(JsonPath.<Integer>read(bob.next("doc.reset"), "$.epoch")).isEqualTo(3);
		alice.close();
		bob.close();
	}

	// ---- metadata and archived documents ----

	@Test
	void titleTypeAndFolderChangeWithoutTouchingTheText() throws Exception {
		Fixture f = fixture("body");
		mockMvc.perform(put("/api/documents/" + f.doc() + "/collab/meta").header("Authorization", f.bob().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Renamed\",\"type\":\"TEST_PLAN\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Renamed")).andExpect(jsonPath("$.type").value("TEST_PLAN")).andExpect(jsonPath("$.content").value("body"));
		mockMvc.perform(put("/api/documents/" + f.doc() + "/collab/meta").header("Authorization", f.viewer().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Nope\",\"type\":\"SRS\"}")).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/documents/" + f.doc() + "/collab/meta").header("Authorization", f.bob().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"X\",\"type\":\"SRS\",\"folderId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isNotFound());
	}

	@Test
	void archivedDocumentsCannotBeEditedLive() throws Exception {
		Fixture f = fixture("body");
		mockMvc.perform(post("/api/documents/" + f.doc() + "/archive").header("Authorization", f.alice().bearer())).andExpect(status().isOk());
		WsClient alice = WsClient.connect(port).authenticate(f.alice());

		assertThat(JsonPath.<Boolean>read(join(alice, f.doc()), "$.canEdit")).isFalse();
		update(alice, f.doc(), 1, "x");
		assertThat(JsonPath.<String>read(alice.next("doc.error"), "$.code")).isEqualTo("FORBIDDEN");
		snapshot(f.alice(), f.doc(), 1, 1, "s", "x", false, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_ARCHIVED"));
		alice.close();
	}

}
