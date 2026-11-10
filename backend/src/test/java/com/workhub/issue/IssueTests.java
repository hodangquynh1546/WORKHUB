package com.workhub.issue;

import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

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
class IssueTests {

	@Autowired
	private MockMvc mockMvc;

	/** Owner (project manager), a project, and members for each interesting role. */
	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, String key, Session po,
			Session dev, Session stakeholder) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		String key = TestApi.uniqueKey();
		UUID project = TestApi.createProject(mockMvc, owner, team, key);
		Session po = member(owner, ws, project, "PRODUCT_OWNER");
		Session dev = member(owner, ws, project, "DEVELOPER");
		Session stakeholder = member(owner, ws, project, "STAKEHOLDER");
		return new Fixture(owner, ws, team, project, key, po, dev, stakeholder);
	}

	private Session member(Session owner, UUID ws, UUID project, String role) throws Exception {
		Session s = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, s, role);
		return s;
	}

	// ---- create ----

	@Test
	void issuesGetSequentialKeysAndSensibleDefaults() throws Exception {
		Fixture f = fixture();

		mockMvc.perform(post("/api/projects/" + f.project() + "/issues").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("type", "TASK", "title", "  Write the API  ", "labels", null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.key").value(f.key() + "-1"))
			.andExpect(jsonPath("$.number").value(1))
			.andExpect(jsonPath("$.title").value("Write the API"))
			.andExpect(jsonPath("$.status").value("TO_DO"))
			.andExpect(jsonPath("$.priority").value("MEDIUM"))
			.andExpect(jsonPath("$.refinement").value("NEW"))
			.andExpect(jsonPath("$.reporter.id").value(f.dev().userId().toString()))
			.andExpect(jsonPath("$.assignee").doesNotExist())
			.andExpect(jsonPath("$.createdAt").isNotEmpty());
		create(f.dev(), f.project(), "BUG", "Crash", null).andExpect(jsonPath("$.key").value(f.key() + "-2"));
	}

	@Test
	void createAppliesEverythingGiven() throws Exception {
		Fixture f = fixture();
		UUID epic = id(create(f.po(), f.project(), "EPIC", "Checkout", null));

		mockMvc.perform(post("/api/projects/" + f.project() + "/issues").header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"USER_STORY\",\"title\":\"Pay by card\",\"priority\":\"HIGH\",\"parentId\":\"" + epic
					+ "\",\"assigneeId\":\"" + f.dev().userId() + "\",\"storyPoints\":5,\"originalEstimateMinutes\":480,"
					+ "\"startDate\":\"2026-11-02\",\"dueDate\":\"2026-11-10\",\"acceptanceCriteria\":\"Card is charged\","
					+ "\"labels\":[\"Payments\",\" payments \",\"UI\"]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parent.id").value(epic.toString()))
			.andExpect(jsonPath("$.epic.id").value(epic.toString()))
			.andExpect(jsonPath("$.assignee.fullName").value("Test User"))
			.andExpect(jsonPath("$.storyPoints").value(5))
			.andExpect(jsonPath("$.remainingEstimateMinutes").value(480))
			.andExpect(jsonPath("$.labels").value(org.hamcrest.Matchers.contains("payments", "ui")))
			.andExpect(jsonPath("$.acceptanceCriteria").value("Card is charged"));
	}

	@Test
	void createValidatesInput() throws Exception {
		Fixture f = fixture();
		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		postIssue(f.dev(), f.project(), json("type", "TASK", "title", " ")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title").exists());
		postIssue(f.dev(), f.project(), "{\"type\":\"TASK\",\"title\":\"x\",\"storyPoints\":101}")
			.andExpect(status().isBadRequest());
		postIssue(f.dev(), f.project(), json("type", "TASK", "title", "x", "startDate", "2026-12-01", "dueDate", "2026-11-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATES"));
		postIssue(f.dev(), f.project(), "{\"type\":\"TASK\",\"title\":\"x\",\"assigneeId\":\"" + outsider.userId() + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_MEMBER"));
	}

	@Test
	void hierarchyRulesAreEnforced() throws Exception {
		Fixture f = fixture();
		UUID epic = id(create(f.po(), f.project(), "EPIC", "Epic", null));
		UUID feature = id(create(f.po(), f.project(), "FEATURE", "Feature", epic));
		UUID story = id(create(f.po(), f.project(), "USER_STORY", "Story", feature));
		UUID task = id(create(f.po(), f.project(), "TASK", "Task", null));

		create(f.po(), f.project(), "SUB_TASK", "Orphan", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PARENT_REQUIRED"));
		create(f.po(), f.project(), "EPIC", "Nested epic", epic).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARENT"));
		create(f.po(), f.project(), "TASK", "Under a task", task).andExpect(status().isBadRequest());
		create(f.po(), f.project(), "SUB_TASK", "Under an epic", epic).andExpect(status().isBadRequest());
		create(f.po(), f.project(), "SUB_TASK", "Fine", story).andExpect(status().isCreated())
			// The epic is found three levels up: sub-task -> story -> feature -> epic.
			.andExpect(jsonPath("$.epic.id").value(epic.toString()));
		create(f.po(), f.project(), "TASK", "Missing parent", UUID.randomUUID()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARENT"));

		// A parent from another project does not count.
		UUID otherProject = TestApi.createProject(mockMvc, f.owner(), f.team(), TestApi.uniqueKey());
		UUID foreignEpic = id(create(f.owner(), otherProject, "EPIC", "Elsewhere", null));
		create(f.po(), f.project(), "TASK", "Cross-project", foreignEpic).andExpect(status().isBadRequest());
	}

	@Test
	void changingTypeKeepsTheHierarchyValid() throws Exception {
		Fixture f = fixture();
		UUID epic = id(create(f.po(), f.project(), "EPIC", "Epic", null));
		UUID task = id(create(f.po(), f.project(), "TASK", "Task", epic));
		create(f.po(), f.project(), "SUB_TASK", "Child", task).andExpect(status().isCreated());

		// A task with a sub-task cannot become an epic or a sub-task.
		update(f.po(), task, "EPIC", "Task", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHILD_TYPE_CONFLICT"));
		update(f.po(), task, "BUG", "Task", epic.toString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("BUG"));
		// An epic cannot be moved under another issue.
		update(f.po(), epic, "EPIC", "Epic", task.toString()).andExpect(status().isBadRequest());
	}

	// ---- permissions ----

	@Test
	void permissionsFollowTheProjectRole() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Work", null));

		create(f.stakeholder(), f.project(), "TASK", "No", null).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", f.stakeholder().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.containsInAnyOrder("VIEW", "COMMENT")));
		update(f.stakeholder(), issue, "TASK", "Edited", null).andExpect(status().isForbidden());
		setStatus(f.stakeholder(), issue, "DONE").andExpect(status().isForbidden());
		comment(f.stakeholder(), issue, "Looks good").andExpect(status().isCreated());

		update(f.dev(), issue, "TASK", "Edited by dev", null).andExpect(status().isOk());
		// Ordering and deleting the backlog belong to the product owner and managers.
		rank(f.dev(), issue, null).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/issues/" + issue).header("Authorization", f.dev().bearer()))
			.andExpect(status().isForbidden());
		rank(f.po(), issue, null).andExpect(status().isOk());
	}

	@Test
	void outsidersCannotSeeIssues() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Secret", null));
		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", outsider.bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
		mockMvc.perform(get("/api/projects/" + f.project() + "/issues").header("Authorization", outsider.bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/issues/" + UUID.randomUUID()).header("Authorization", f.po().bearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/issues/" + issue + "/comments").header("Authorization", outsider.bearer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void archivedProjectsAreReadOnly() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Work", null));
		mockMvc.perform(put("/api/projects/" + f.project() + "/status").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("status", "ARCHIVED"))).andExpect(status().isOk());

		create(f.po(), f.project(), "TASK", "More", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		setStatus(f.dev(), issue, "DONE").andExpect(status().isConflict());
		comment(f.dev(), issue, "hi").andExpect(status().isConflict());
		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk());
	}

	// ---- workflow, assignment, history ----

	@Test
	void statusChangesTrackFlowTimestampsAndHistory() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Work", null));

		setStatus(f.dev(), issue, "IN_PROGRESS").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.startedAt").isNotEmpty())
			.andExpect(jsonPath("$.doneAt").doesNotExist());
		setStatus(f.dev(), issue, "DONE").andExpect(jsonPath("$.doneAt").isNotEmpty());
		// Reopening clears the done timestamp but keeps when work first started.
		setStatus(f.dev(), issue, "TESTING").andExpect(jsonPath("$.doneAt").doesNotExist())
			.andExpect(jsonPath("$.startedAt").isNotEmpty());

		mockMvc.perform(get("/api/issues/" + issue + "/activity").header("Authorization", f.dev().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].field").value("status"))
			.andExpect(jsonPath("$[0].oldValue").value("DONE"))
			.andExpect(jsonPath("$[0].newValue").value("TESTING"))
			.andExpect(jsonPath("$[3].kind").value("CREATED"))
			.andExpect(jsonPath("$[0].actor.fullName").value("Test User"));
	}

	@Test
	void updatingFieldsRecordsOnlyWhatChanged() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Work", null));

		mockMvc.perform(put("/api/issues/" + issue).header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"TASK\",\"title\":\"Work\",\"description\":\"Long text\",\"priority\":\"HIGH\","
					+ "\"refinement\":\"READY\",\"storyPoints\":8,\"labels\":[\"api\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.priority").value("HIGH"))
			.andExpect(jsonPath("$.storyPoints").value(8));

		String body = mockMvc.perform(get("/api/issues/" + issue + "/activity").header("Authorization", f.po().bearer()))
			.andReturn().getResponse().getContentAsString();
		List<String> fields = JsonPath.read(body, "$[?(@.kind == 'CHANGED')].field");
		assertThat(fields).containsExactlyInAnyOrder("description", "priority", "refinement", "storyPoints", "labels");
		// Long text keeps only the fact of the change.
		List<String> descriptionValues = JsonPath.read(body, "$[?(@.field == 'description')].newValue");
		assertThat(descriptionValues).containsExactly((String) null);
		List<String> pointValues = JsonPath.read(body, "$[?(@.field == 'storyPoints')].newValue");
		assertThat(pointValues).containsExactly("8");
		List<String> priorityValues = JsonPath.read(body, "$[?(@.field == 'priority')].oldValue");
		assertThat(priorityValues).containsExactly("MEDIUM");
	}

	@Test
	void assigningRequiresAProjectMemberAndMembersLeavingUnassignThem() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Work", null));
		Session outsider = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		assign(f.po(), issue, outsider.userId().toString()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_MEMBER"));
		assign(f.po(), issue, f.dev().userId().toString()).andExpect(status().isOk())
			.andExpect(jsonPath("$.assignee.id").value(f.dev().userId().toString()));
		mockMvc.perform(get("/api/projects/" + f.project() + "/issues?assigneeId=" + f.dev().userId())
			.header("Authorization", f.po().bearer())).andExpect(jsonPath("$.total").value(1));

		mockMvc.perform(delete("/api/projects/" + f.project() + "/members/" + f.dev().userId())
			.header("Authorization", f.owner().bearer())).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.issue.assignee").doesNotExist());

		assign(f.po(), issue, f.stakeholder().userId().toString()).andExpect(status().isOk());
		assign(f.po(), issue, null).andExpect(status().isOk()).andExpect(jsonPath("$.assignee").doesNotExist());
	}

	// ---- backlog ----

	@Test
	void newIssuesJoinTheBottomAndCanBeReordered() throws Exception {
		Fixture f = fixture();
		UUID a = id(create(f.po(), f.project(), "TASK", "A", null));
		UUID b = id(create(f.po(), f.project(), "TASK", "B", null));
		UUID c = id(create(f.po(), f.project(), "TASK", "C", null));
		assertThat(order(f)).containsExactly("A", "B", "C");

		rank(f.po(), c, null).andExpect(status().isOk());
		assertThat(order(f)).containsExactly("C", "A", "B");
		rank(f.po(), c, b).andExpect(status().isOk());
		assertThat(order(f)).containsExactly("A", "B", "C");
		rank(f.po(), a, b).andExpect(status().isOk());
		assertThat(order(f)).containsExactly("B", "A", "C");
		rank(f.po(), b, b).andExpect(status().isOk());
		assertThat(order(f)).containsExactly("B", "A", "C");
		rank(f.po(), a, UUID.randomUUID()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RANK_TARGET"));
	}

	@Test
	void ranksAreRebalancedWhenTheGapRunsOut() throws Exception {
		Fixture f = fixture();
		List<UUID> ids = new ArrayList<>();
		for (String title : List.of("A", "B", "C")) {
			ids.add(id(create(f.po(), f.project(), "TASK", title, null)));
		}
		// Moving C to the top over and over halves the gap each time; 1024 allows ~10 halvings.
		for (int i = 0; i < 14; i++) {
			rank(f.po(), ids.get(2), null).andExpect(status().isOk());
			rank(f.po(), ids.get(1), null).andExpect(status().isOk());
			rank(f.po(), ids.get(0), null).andExpect(status().isOk());
		}
		assertThat(order(f)).containsExactly("A", "B", "C");
		rank(f.po(), ids.get(2), ids.get(0)).andExpect(status().isOk());
		assertThat(order(f)).containsExactly("A", "C", "B");
	}

	@Test
	void backlogFiltersAndSearch() throws Exception {
		Fixture f = fixture();
		UUID epic = id(create(f.po(), f.project(), "EPIC", "Payments", null));
		UUID feature = id(create(f.po(), f.project(), "FEATURE", "Cards", epic));
		UUID story = id(postIssue(f.po(), f.project(), "{\"type\":\"USER_STORY\",\"title\":\"Pay by card\",\"parentId\":\""
				+ feature + "\",\"priority\":\"HIGH\",\"labels\":[\"checkout\"]}"));
		UUID bug = id(postIssue(f.po(), f.project(), "{\"type\":\"BUG\",\"title\":\"Crash on 100% load_test\",\"priority\":\"CRITICAL\"}"));
		create(f.po(), f.project(), "TASK", "Unrelated", null);
		assign(f.po(), bug, f.dev().userId().toString());
		setStatus(f.dev(), bug, "IN_PROGRESS");

		assertThat(titles(f, "")).hasSize(5);
		assertThat(titles(f, "type=BUG")).containsExactly("Crash on 100% load_test");
		assertThat(titles(f, "type=BUG&type=EPIC")).containsExactlyInAnyOrder("Crash on 100% load_test", "Payments");
		assertThat(titles(f, "status=IN_PROGRESS")).containsExactly("Crash on 100% load_test");
		assertThat(titles(f, "priority=HIGH&priority=CRITICAL")).containsExactlyInAnyOrder("Pay by card", "Crash on 100% load_test");
		assertThat(titles(f, "assigneeId=" + f.dev().userId())).containsExactly("Crash on 100% load_test");
		assertThat(titles(f, "unassigned=true")).hasSize(4);
		assertThat(titles(f, "label=Checkout")).containsExactly("Pay by card");
		assertThat(titles(f, "q=PAY")).containsExactlyInAnyOrder("Payments", "Pay by card");
		// MockMvc encodes the URL itself, so '%' is passed raw; the service must treat '%' and '_' literally.
		assertThat(titles(f, "q=100% load_")).containsExactly("Crash on 100% load_test");
		assertThat(titles(f, "q=%")).containsExactly("Crash on 100% load_test");
		assertThat(titles(f, "q=a_c")).isEmpty();
		assertThat(titles(f, "q=" + f.key() + "-" + 4)).containsExactly("Crash on 100% load_test");
		assertThat(titles(f, "q=4")).contains("Crash on 100% load_test");
		// Everything below the epic, at any depth.
		assertThat(titles(f, "epicId=" + epic)).containsExactlyInAnyOrder("Cards", "Pay by card");
		assertThat(titles(f, "refinement=READY")).isEmpty();
		mockMvc.perform(get("/api/projects/" + f.project() + "/issue-labels").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("checkout")));
		assertThat(story).isNotNull();
	}

	@Test
	void listsArePagedAndSortable() throws Exception {
		Fixture f = fixture();
		for (int i = 1; i <= 5; i++) {
			create(f.po(), f.project(), "TASK", "T" + i, null);
		}

		mockMvc.perform(get("/api/projects/" + f.project() + "/issues?size=2&page=1").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].title").value("T3"))
			.andExpect(jsonPath("$.total").value(5))
			.andExpect(jsonPath("$.totalPages").value(3));
		mockMvc.perform(get("/api/projects/" + f.project() + "/issues?size=1&sort=created").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.items[0].title").value("T5"));
		mockMvc.perform(get("/api/projects/" + f.project() + "/issues?sort=bogus").header("Authorization", f.po().bearer()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listReportsChildProgress() throws Exception {
		Fixture f = fixture();
		UUID story = id(create(f.po(), f.project(), "USER_STORY", "Story", null));
		UUID one = id(create(f.po(), f.project(), "SUB_TASK", "One", story));
		create(f.po(), f.project(), "SUB_TASK", "Two", story);
		setStatus(f.dev(), one, "DONE");

		mockMvc.perform(get("/api/projects/" + f.project() + "/issues?type=USER_STORY").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.items[0].childCount").value(2))
			.andExpect(jsonPath("$.items[0].childDoneCount").value(1))
			.andExpect(jsonPath("$.items[0].description").doesNotExist());
		mockMvc.perform(get("/api/issues/" + story).header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.children.length()").value(2))
			.andExpect(jsonPath("$.children[0].title").value("One"));
	}

	@Test
	void deletedIssuesDisappearButTheirKeysAreNeverReused() throws Exception {
		Fixture f = fixture();
		UUID epic = id(create(f.po(), f.project(), "EPIC", "Epic", null));
		UUID child = id(create(f.po(), f.project(), "TASK", "Child", epic));

		mockMvc.perform(delete("/api/issues/" + epic).header("Authorization", f.po().bearer()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HAS_CHILDREN"));
		mockMvc.perform(delete("/api/issues/" + child).header("Authorization", f.po().bearer()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/issues/" + child).header("Authorization", f.po().bearer()))
			.andExpect(status().isNotFound());
		assertThat(titles(f, "")).containsExactly("Epic");
		mockMvc.perform(delete("/api/issues/" + epic).header("Authorization", f.po().bearer()))
			.andExpect(status().isNoContent());

		create(f.po(), f.project(), "TASK", "New", null).andExpect(jsonPath("$.number").value(3));
	}

	// ---- comments & checklist ----

	@Test
	void commentsBelongToTheirAuthors() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Work", null));
		String id = JsonPath.read(comment(f.dev(), issue, "  First  ").andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("First"))
			.andReturn().getResponse().getContentAsString(), "$.id");

		mockMvc.perform(put("/api/comments/" + id).header("Authorization", f.po().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("body", "Hijack"))).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/comments/" + id).header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("body", "Edited")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.body").value("Edited"));
		mockMvc.perform(delete("/api/comments/" + id).header("Authorization", f.stakeholder().bearer()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/issues/" + issue + "/comments").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].author.id").value(f.dev().userId().toString()));
		// The project manager can remove anyone's comment.
		mockMvc.perform(delete("/api/comments/" + id).header("Authorization", f.owner().bearer()))
			.andExpect(status().isNoContent());
		comment(f.dev(), issue, " ").andExpect(status().isBadRequest());
	}

	@Test
	void checklistItemsCanBeAddedToggledAndRemoved() throws Exception {
		Fixture f = fixture();
		UUID issue = id(create(f.po(), f.project(), "TASK", "Work", null));
		String first = JsonPath.read(mockMvc.perform(post("/api/issues/" + issue + "/checklist")
			.header("Authorization", f.dev().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(json("text", "Write tests"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.done").value(false)).andReturn().getResponse().getContentAsString(), "$.id");
		mockMvc.perform(post("/api/issues/" + issue + "/checklist").header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("text", "Ship"))).andExpect(status().isCreated());

		mockMvc.perform(put("/api/checklist/" + first).header("Authorization", f.dev().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
			.andExpect(jsonPath("$.done").value(true)).andExpect(jsonPath("$.text").value("Write tests"));
		mockMvc.perform(put("/api/checklist/" + first).header("Authorization", f.stakeholder().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"done\":false}")).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.checklist.length()").value(2))
			.andExpect(jsonPath("$.checklist[0].text").value("Write tests"));
		mockMvc.perform(delete("/api/checklist/" + first).header("Authorization", f.dev().bearer()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/issues/" + issue).header("Authorization", f.dev().bearer()))
			.andExpect(jsonPath("$.checklist.length()").value(1));
	}

	// ---- stats & concurrency ----

	@Test
	void statsCountWorkItemsPointsAndOverdue() throws Exception {
		Fixture f = fixture();
		create(f.po(), f.project(), "EPIC", "Epics are not counted", null);
		UUID a = id(postIssue(f.po(), f.project(), "{\"type\":\"TASK\",\"title\":\"A\",\"storyPoints\":3,\"dueDate\":\"2026-01-10\"}"));
		UUID b = id(postIssue(f.po(), f.project(), "{\"type\":\"TASK\",\"title\":\"B\",\"storyPoints\":5,\"dueDate\":\"2026-01-10\"}"));
		UUID c = id(postIssue(f.po(), f.project(), "{\"type\":\"BUG\",\"title\":\"C\",\"storyPoints\":2}"));
		setStatus(f.dev(), a, "DONE");
		setStatus(f.dev(), b, "CODE_REVIEW");
		assertThat(c).isNotNull();

		mockMvc.perform(get("/api/projects/" + f.project() + "/stats?today=2026-02-01").header("Authorization", f.po().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.toDo").value(1))
			.andExpect(jsonPath("$.inProgress").value(1))
			.andExpect(jsonPath("$.done").value(1))
			// A is overdue by date but done; only B counts.
			.andExpect(jsonPath("$.overdue").value(1))
			.andExpect(jsonPath("$.totalPoints").value(10))
			.andExpect(jsonPath("$.donePoints").value(3));
		mockMvc.perform(get("/api/projects/" + f.project() + "/stats?today=2025-12-01").header("Authorization", f.po().bearer()))
			.andExpect(jsonPath("$.overdue").value(0));
	}

	@Test
	void concurrentCreatesGetDistinctKeys() throws Exception {
		Fixture f = fixture();
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Callable<Integer>> jobs = IntStream.range(0, threads).<Callable<Integer>>mapToObj((i) -> () -> {
				String body = create(f.po(), f.project(), "TASK", "Parallel " + i, null)
					.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
				return JsonPath.<Integer>read(body, "$.number");
			}).toList();
			List<Integer> numbers = new ArrayList<>();
			for (Future<Integer> future : pool.invokeAll(jobs)) {
				numbers.add(future.get());
			}
			assertThat(numbers).doesNotHaveDuplicates().hasSize(threads);
			assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, threads).boxed().toList());
		}
		finally {
			pool.shutdownNow();
		}
	}

	// ---- helpers ----

	private ResultActions create(Session caller, UUID project, String type, String title, UUID parent) throws Exception {
		return postIssue(caller, project, (parent == null) ? json("type", type, "title", title)
				: "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"parentId\":\"" + parent + "\"}");
	}

	private ResultActions postIssue(Session caller, UUID project, String body) throws Exception {
		return mockMvc.perform(post("/api/projects/" + project + "/issues").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private UUID id(ResultActions created) throws Exception {
		return UUID.fromString(JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id"));
	}

	private ResultActions update(Session caller, UUID issue, String type, String title, String parentId) throws Exception {
		String parent = (parentId == null) ? "null" : "\"" + parentId + "\"";
		return mockMvc.perform(put("/api/issues/" + issue).header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"priority\":\"MEDIUM\","
					+ "\"refinement\":\"NEW\",\"parentId\":" + parent + "}"));
	}

	private ResultActions setStatus(Session caller, UUID issue, String status) throws Exception {
		return mockMvc.perform(put("/api/issues/" + issue + "/status").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("status", status)));
	}

	private ResultActions assign(Session caller, UUID issue, String assigneeId) throws Exception {
		return mockMvc.perform(put("/api/issues/" + issue + "/assignee").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("assigneeId", assigneeId)));
	}

	private ResultActions rank(Session caller, UUID issue, UUID after) throws Exception {
		return mockMvc.perform(put("/api/issues/" + issue + "/rank").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("afterIssueId", (after == null) ? null : after.toString())));
	}

	private ResultActions comment(Session caller, UUID issue, String body) throws Exception {
		return mockMvc.perform(post("/api/issues/" + issue + "/comments").header("Authorization", caller.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json("body", body)));
	}

	private List<String> order(Fixture f) throws Exception {
		return titles(f, "");
	}

	private List<String> titles(Fixture f, String query) throws Exception {
		String body = mockMvc.perform(get("/api/projects/" + f.project() + "/issues?" + query)
			.header("Authorization", f.po().bearer())).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.items[*].title");
	}

}
