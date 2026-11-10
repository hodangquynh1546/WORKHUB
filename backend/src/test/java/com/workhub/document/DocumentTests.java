package com.workhub.document;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentTests {

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, Session dev, Session other,
			Session stakeholder) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		return new Fixture(owner, ws, team, project, member(owner, ws, project, "DEVELOPER"),
				member(owner, ws, project, "DEVELOPER"), member(owner, ws, project, "STAKEHOLDER"));
	}

	private Session member(Session owner, UUID ws, UUID project, String role) throws Exception {
		Session s = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, s, role);
		return s;
	}

	// ---- create and read ----

	@Test
	void membersCreateDocumentsAndEveryoneInTheProjectCanRead() throws Exception {
		Fixture f = fixture();

		UUID doc = created(createDoc(f.dev(), f.project(), "Project Charter", "PROJECT_CHARTER", "# Goals\n\nShip **it**", null));
		fetch("/api/documents/" + doc, f.stakeholder())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Project Charter"))
			.andExpect(jsonPath("$.type").value("PROJECT_CHARTER"))
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.content").value("# Goals\n\nShip **it**"))
			.andExpect(jsonPath("$.createdBy.id").value(f.dev().userId().toString()))
			.andExpect(jsonPath("$.canEdit").value(false))
			.andExpect(jsonPath("$.canDelete").value(false));
		fetch("/api/documents/" + doc, f.dev()).andExpect(jsonPath("$.canEdit").value(true));
		createDoc(f.stakeholder(), f.project(), "Nope", "OTHER", "x", null).andExpect(status().isForbidden());
		createDoc(f.dev(), f.project(), " ", "OTHER", "x", null).andExpect(status().isBadRequest());

		Session outsider = TestApi.register(mockMvc);
		fetch("/api/documents/" + doc, outsider).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		fetch("/api/projects/" + f.project() + "/documents", outsider).andExpect(status().isNotFound());
	}

	@Test
	void listsSearchTitleAndTextAndFilterByTypeFolderAndStatus() throws Exception {
		Fixture f = fixture();
		UUID folder = folderId(createFolder(f.dev(), f.project(), "Specs", null));
		UUID srs = created(createDoc(f.dev(), f.project(), "Login SRS", "SRS", "Users sign in with email", folder));
		UUID plan = created(createDoc(f.dev(), f.project(), "Test plan", "TEST_PLAN", "Covers the PAYMENT flow", null));

		list(f, "").andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.items[0].excerpt").value(org.hamcrest.Matchers.notNullValue()));
		list(f, "q=payment").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(plan.toString()));
		list(f, "q=LOGIN").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(srs.toString()));
		list(f, "q=100%25").andExpect(jsonPath("$.total").value(0));
		list(f, "type=SRS").andExpect(jsonPath("$.total").value(1));
		list(f, "folderId=" + folder).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(srs.toString()));
		list(f, "unfiled=true").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(plan.toString()));

		act("/api/documents/" + plan + "/archive", f.dev(), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED")).andExpect(jsonPath("$.canEdit").value(false));
		list(f, "").andExpect(jsonPath("$.total").value(1));
		list(f, "status=ARCHIVED").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(plan.toString()));
		list(f, "status=ACTIVE&status=ARCHIVED").andExpect(jsonPath("$.total").value(2));
	}

	// ---- editing, versions, conflicts ----

	@Test
	void everyTextChangeAddsAVersionAndOldOnesCanBeRestored() throws Exception {
		Fixture f = fixture();
		UUID doc = created(createDoc(f.dev(), f.project(), "Design", "TECHNICAL_DESIGN", "first", null));

		update(f.dev(), doc, "Design", "TECHNICAL_DESIGN", "second", 1, "added details").andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.content").value("second"));
		// Only the type changes: no new version.
		update(f.other(), doc, "Design", "SRS", "second", 2, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.type").value("SRS"))
			.andExpect(jsonPath("$.updatedBy.id").value(f.other().userId().toString()));
		update(f.dev(), doc, "Design v3", "SRS", "third", 2, null).andExpect(jsonPath("$.version").value(3));

		fetch("/api/documents/" + doc + "/versions", f.stakeholder()).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].version").value(3))
			.andExpect(jsonPath("$[1].note").value("added details"))
			.andExpect(jsonPath("$[2].version").value(1));
		fetch("/api/documents/" + doc + "/versions/1", f.stakeholder())
			.andExpect(jsonPath("$.content").value("first")).andExpect(jsonPath("$.title").value("Design"));
		fetch("/api/documents/" + doc + "/versions/9", f.dev()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"));

		act("/api/documents/" + doc + "/versions/1/restore", f.stakeholder(), null).andExpect(status().isForbidden());
		act("/api/documents/" + doc + "/versions/1/restore", f.other(), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(4))
			.andExpect(jsonPath("$.content").value("first")).andExpect(jsonPath("$.title").value("Design"));
		// History is only ever appended to.
		fetch("/api/documents/" + doc + "/versions", f.dev()).andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].note").value("Khôi phục từ phiên bản 1"));
		fetch("/api/documents/" + doc + "/versions/3", f.dev()).andExpect(jsonPath("$.content").value("third"));
	}

	@Test
	void aSaveBasedOnAStaleVersionIsRefused() throws Exception {
		Fixture f = fixture();
		UUID doc = created(createDoc(f.dev(), f.project(), "Doc", "OTHER", "v1", null));
		update(f.dev(), doc, "Doc", "OTHER", "v2 by dev", 1, null).andExpect(status().isOk());

		update(f.other(), doc, "Doc", "OTHER", "v2 by other", 1, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_CONFLICT"));
		fetch("/api/documents/" + doc, f.dev()).andExpect(jsonPath("$.content").value("v2 by dev"));
		update(f.dev(), doc, "Doc", "OTHER", "x", null, null).andExpect(status().isBadRequest());
	}

	@Test
	void twoSimultaneousSavesFromTheSameVersionCannotBothWin() throws Exception {
		Fixture f = fixture();
		UUID doc = created(createDoc(f.dev(), f.project(), "Doc", "OTHER", "v1", null));

		var pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Integer>> saves = List.of(
					() -> update(f.dev(), doc, "Doc", "OTHER", "from dev", 1, null).andReturn().getResponse().getStatus(),
					() -> update(f.other(), doc, "Doc", "OTHER", "from other", 1, null).andReturn().getResponse().getStatus());
			List<Integer> statuses = pool.invokeAll(saves).stream().map((r) -> {
				try {
					return r.get();
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).toList();
			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		}
		finally {
			pool.shutdown();
		}
		fetch("/api/documents/" + doc + "/versions", f.dev()).andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void archivedDocumentsAreReadOnlyUntilUnarchived() throws Exception {
		Fixture f = fixture();
		UUID doc = created(createDoc(f.dev(), f.project(), "Doc", "OTHER", "v1", null));
		act("/api/documents/" + doc + "/archive", f.dev(), null).andExpect(status().isOk());

		update(f.dev(), doc, "Doc", "OTHER", "v2", 1, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_ARCHIVED"));
		act("/api/documents/" + doc + "/versions/1/restore", f.dev(), null).andExpect(status().isConflict());
		fetch("/api/documents/" + doc, f.stakeholder()).andExpect(status().isOk());

		act("/api/documents/" + doc + "/unarchive", f.dev(), null).andExpect(status().isOk());
		update(f.dev(), doc, "Doc", "OTHER", "v2", 1, null).andExpect(status().isOk());
	}

	// ---- deleting ----

	@Test
	void onlyTheAuthorOrAManagerDeletesADocument() throws Exception {
		Fixture f = fixture();
		UUID doc = created(createDoc(f.dev(), f.project(), "Doc", "OTHER", "x", null));

		del("/api/documents/" + doc, f.other()).andExpect(status().isForbidden());
		del("/api/documents/" + doc, f.dev()).andExpect(status().isNoContent());
		fetch("/api/documents/" + doc, f.dev()).andExpect(status().isNotFound());
		list(f, "").andExpect(jsonPath("$.total").value(0));

		UUID second = created(createDoc(f.dev(), f.project(), "Doc 2", "OTHER", "x", null));
		del("/api/documents/" + second, f.owner()).andExpect(status().isNoContent());
	}

	// ---- folders ----

	@Test
	void foldersNestHaveUniqueNamesAndAreDeletedOnlyWhenEmpty() throws Exception {
		Fixture f = fixture();
		UUID root = folderId(createFolder(f.dev(), f.project(), "Specs", null));
		UUID child = folderId(createFolder(f.dev(), f.project(), "API", root));
		createFolder(f.dev(), f.project(), "specs", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FOLDER_NAME_TAKEN"));
		// The same name under another parent is fine.
		createFolder(f.dev(), f.project(), "API", null).andExpect(status().isCreated());
		createFolder(f.stakeholder(), f.project(), "Nope", null).andExpect(status().isForbidden());

		UUID level3 = folderId(createFolder(f.dev(), f.project(), "L3", child));
		UUID level4 = folderId(createFolder(f.dev(), f.project(), "L4", level3));
		createFolder(f.dev(), f.project(), "L5", level4).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FOLDER_TOO_DEEP"));

		UUID doc = created(createDoc(f.dev(), f.project(), "In API", "API_DOCUMENTATION", "x", child));
		fetch("/api/projects/" + f.project() + "/document-folders", f.stakeholder()).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name=='API' && @.parentId=='" + root + "')].documentCount").value(1));
		del("/api/document-folders/" + root, f.dev()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));
		del("/api/document-folders/" + child, f.dev()).andExpect(status().isConflict());

		// Moving the document out and removing the leaf folders empties the tree.
		update(f.dev(), doc, "In API", "API_DOCUMENTATION", "x", 1, null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.folderId").doesNotExist());
		del("/api/document-folders/" + level4, f.dev()).andExpect(status().isNoContent());
		del("/api/document-folders/" + level3, f.dev()).andExpect(status().isNoContent());
		del("/api/document-folders/" + child, f.dev()).andExpect(status().isNoContent());
		mockMvc.perform(put("/api/document-folders/" + root).header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("name", "Specifications")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Specifications"));
		del("/api/document-folders/" + root, f.dev()).andExpect(status().isNoContent());
	}

	@Test
	void aFolderFromAnotherProjectCannotBeUsed() throws Exception {
		Fixture f = fixture();
		Fixture other = fixture();
		UUID foreign = folderId(createFolder(other.dev(), other.project(), "Theirs", null));

		createDoc(f.dev(), f.project(), "Doc", "OTHER", "x", foreign).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("FOLDER_NOT_FOUND"));
		del("/api/document-folders/" + foreign, f.dev()).andExpect(status().isNotFound());
	}

	// ---- attachments ----

	@Test
	void filesAreAttachedDownloadedAndRemoved() throws Exception {
		Fixture f = fixture();
		UUID doc = created(createDoc(f.dev(), f.project(), "Doc", "OTHER", "x", null));
		byte[] bytes = "hello, attachment".getBytes();

		String body = upload(f.dev(), doc, "..\\evil/..\\report.pdf", "application/pdf", bytes).andExpect(status().isCreated())
			.andExpect(jsonPath("$.fileName").value("report.pdf"))
			.andExpect(jsonPath("$.sizeBytes").value(bytes.length))
			.andReturn().getResponse().getContentAsString();
		String attachment = JsonPath.read(body, "$.id");
		upload(f.stakeholder(), doc, "x.txt", "text/plain", bytes).andExpect(status().isForbidden());
		upload(f.dev(), doc, "empty.txt", "text/plain", new byte[0]).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FILE_EMPTY"));

		fetch("/api/documents/" + doc, f.stakeholder()).andExpect(jsonPath("$.attachments.length()").value(1))
			.andExpect(jsonPath("$.attachments[0].uploadedBy.id").value(f.dev().userId().toString()));
		list(f, "").andExpect(jsonPath("$.items[0].attachmentCount").value(1));
		// Readers can download; the browser is told to save it, never to render it.
		mockMvc.perform(get("/api/documents/" + doc + "/attachments/" + attachment)
			.header("Authorization", f.stakeholder().bearer()))
			.andExpect(status().isOk())
			.andExpect(content().bytes(bytes))
			.andExpect(header().string("Content-Type", "application/octet-stream"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
		mockMvc.perform(get("/api/documents/" + doc + "/attachments/" + attachment)
			.header("Authorization", TestApi.register(mockMvc).bearer())).andExpect(status().isNotFound());

		del("/api/documents/" + doc + "/attachments/" + attachment, f.stakeholder()).andExpect(status().isForbidden());
		del("/api/documents/" + doc + "/attachments/" + attachment, f.other()).andExpect(status().isNoContent());
		fetch("/api/documents/" + doc, f.dev()).andExpect(jsonPath("$.attachments.length()").value(0));
	}

	// ---- links ----

	@Test
	void documentsLinkToIssuesAndSprintsOfTheSameProject() throws Exception {
		Fixture f = fixture();
		UUID doc = created(createDoc(f.dev(), f.project(), "Doc", "OTHER", "x", null));
		UUID issue = created(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", f.dev().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"Build it\"}")));
		UUID sprint = created(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Sprint 1\",\"startDate\":\"2026-03-02\",\"endDate\":\"2026-03-13\"}")));

		link(f.dev(), doc, "ISSUE", issue).andExpect(status().isOk()).andExpect(jsonPath("$.links.length()").value(1))
			.andExpect(jsonPath("$.links[0].label").value(org.hamcrest.Matchers.endsWith(" Build it")))
			.andExpect(jsonPath("$.links[0].detail").value("TO_DO"));
		link(f.dev(), doc, "ISSUE", issue).andExpect(jsonPath("$.links.length()").value(1));
		link(f.dev(), doc, "SPRINT", sprint).andExpect(jsonPath("$.links.length()").value(2));
		link(f.stakeholder(), doc, "SPRINT", sprint).andExpect(status().isForbidden());

		list(f, "linkedType=ISSUE&linkedId=" + issue).andExpect(jsonPath("$.total").value(1));
		list(f, "linkedType=SPRINT&linkedId=" + UUID.randomUUID()).andExpect(jsonPath("$.total").value(0));

		Fixture other = fixture();
		UUID foreignIssue = created(mockMvc.perform(post("/api/projects/" + other.project() + "/issues")
			.header("Authorization", other.dev().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"Theirs\"}")));
		link(f.dev(), doc, "ISSUE", foreignIssue).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("LINK_TARGET_NOT_FOUND"));

		del("/api/documents/" + doc + "/links/ISSUE/" + issue, f.dev()).andExpect(status().isOk())
			.andExpect(jsonPath("$.links.length()").value(1));
		list(f, "linkedType=ISSUE&linkedId=" + issue).andExpect(jsonPath("$.total").value(0));
	}

	// ---- helpers ----

	private ResultActions createDoc(Session caller, UUID project, String title, String type, String content, UUID folder)
			throws Exception {
		String body = "{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"content\":" + quote(content)
				+ ((folder != null) ? ",\"folderId\":\"" + folder + "\"" : "") + "}";
		return mockMvc.perform(post("/api/projects/" + project + "/documents").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions update(Session caller, UUID doc, String title, String type, String content, Integer baseVersion,
			String note) throws Exception {
		return update(caller, doc, title, type, content, baseVersion, note, null);
	}

	/** {@code folder} is sent as given, so null moves the document out of its folder. */
	private ResultActions update(Session caller, UUID doc, String title, String type, String content, Integer baseVersion,
			String note, UUID folder) throws Exception {
		String body = "{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"content\":" + quote(content)
				+ ((baseVersion != null) ? ",\"baseVersion\":" + baseVersion : "")
				+ ((note != null) ? ",\"note\":\"" + note + "\"" : "")
				+ ((folder != null) ? ",\"folderId\":\"" + folder + "\"" : "") + "}";
		return mockMvc.perform(put("/api/documents/" + doc).header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions createFolder(Session caller, UUID project, String name, UUID parent) throws Exception {
		String body = "{\"name\":\"" + name + "\"" + ((parent != null) ? ",\"parentId\":\"" + parent + "\"" : "") + "}";
		return mockMvc.perform(post("/api/projects/" + project + "/document-folders")
			.header("Authorization", caller.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions link(Session caller, UUID doc, String type, UUID target) throws Exception {
		return mockMvc.perform(post("/api/documents/" + doc + "/links").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetType\":\"" + type + "\",\"targetId\":\"" + target + "\"}"));
	}

	private ResultActions upload(Session caller, UUID doc, String name, String type, byte[] bytes) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.POST, "/api/documents/" + doc + "/attachments")
			.file(new MockMultipartFile("file", name, type, bytes)).header("Authorization", caller.bearer()));
	}

	private ResultActions list(Fixture f, String query) throws Exception {
		return mockMvc.perform(get("/api/projects/" + f.project() + "/documents" + (query.isEmpty() ? "" : "?" + query))
			.header("Authorization", f.dev().bearer())).andExpect(status().isOk());
	}

	private ResultActions fetch(String path, Session caller) throws Exception {
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
			.header("Authorization", caller.bearer()));
	}

	private ResultActions act(String path, Session caller, String body) throws Exception {
		var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
			.header("Authorization", caller.bearer());
		return mockMvc.perform((body == null) ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions del(String path, Session caller) throws Exception {
		return mockMvc.perform(delete(path).header("Authorization", caller.bearer()));
	}

	private UUID created(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id"));
	}

	private UUID folderId(ResultActions result) throws Exception {
		return created(result);
	}

	private static String quote(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

}
