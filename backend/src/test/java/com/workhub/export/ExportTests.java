package com.workhub.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

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
class ExportTests {

	@Autowired
	private MockMvc mockMvc;

	private record Fixture(Session owner, UUID workspace, UUID team, UUID project, Session viewer) {
	}

	private Fixture fixture() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Backend");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		Session viewer = TestApi.joinWorkspace(mockMvc, owner, ws, "MEMBER");
		TestApi.addToProject(mockMvc, owner, project, viewer, "STAKEHOLDER");
		return new Fixture(owner, ws, team, project, viewer);
	}

	// ---- CSV and Excel files ----

	@Test
	void csvIsUtf8WithBomQuotesWhatNeedsItAndDefusesFormulas() throws Exception {
		Table table = Table.of("T", List.of("Tên", "Giá trị"), List.of(
				List.<Object>of("Có, dấu phẩy", 1), List.<Object>of("Dòng \"trích dẫn\"", 2.5), List.<Object>of("=HYPERLINK(\"http://x\")", 3),
				java.util.Arrays.<Object>asList("+cmd", null), List.<Object>of("-5 là chuỗi", true), List.<Object>of("Tiếng Việt: ặ ỳ đ", -7)));

		String csv = new String(Exports.bytes(table, ExportFormat.CSV), StandardCharsets.UTF_8);

		assertThat(csv).startsWith("﻿Tên,Giá trị\r\n");
		assertThat(csv).contains("\"Có, dấu phẩy\",1\r\n").contains("\"Dòng \"\"trích dẫn\"\"\",2.5\r\n");
		// Text that starts like a formula is neutralised; real numbers (even negative) are left alone.
		assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\",3\r\n").contains("'+cmd,\r\n").contains("'-5 là chuỗi,true\r\n");
		assertThat(csv).contains("Tiếng Việt: ặ ỳ đ,-7\r\n");
	}

	@Test
	void xlsxIsAWellFormedWorkbookWithTypedCells() throws Exception {
		Table table = Table.of("Sheet: with/bad*chars?", List.of("Tên", "Số", "Đúng"), List.of(
				List.<Object>of("A & B <tag>", 42, true), List.<Object>of("=1+1", 3.5, false), java.util.Arrays.<Object>asList("ctrl\u0001char", null, true)));

		Map<String, byte[]> parts = unzip(Exports.bytes(table, ExportFormat.XLSX));

		assertThat(parts.keySet()).contains("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml", "xl/styles.xml");
		for (byte[] xml : parts.values()) {
			DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml)); // well-formed
		}
		Document sheet = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(parts.get("xl/worksheets/sheet1.xml")));
		Map<String, String> cells = new HashMap<>();
		NodeList all = sheet.getElementsByTagName("c");
		for (int i = 0; i < all.getLength(); i++) {
			var c = all.item(i);
			cells.put(c.getAttributes().getNamedItem("r").getNodeValue(), c.getTextContent() + "|" + (c.getAttributes().getNamedItem("t") == null ? "n" : c.getAttributes().getNamedItem("t").getNodeValue()));
		}
		assertThat(cells.get("A1")).isEqualTo("Tên|inlineStr");
		assertThat(cells.get("A2")).isEqualTo("A & B <tag>|inlineStr");
		assertThat(cells.get("B2")).isEqualTo("42|n");
		assertThat(cells.get("C2")).isEqualTo("1|b");
		assertThat(cells.get("A3")).isEqualTo("'=1+1|inlineStr"); // not a formula
		assertThat(cells.get("B3")).isEqualTo("3.5|n");
		assertThat(cells.get("A4")).isEqualTo("ctrlchar|inlineStr"); // control character dropped
		assertThat(cells).doesNotContainKey("B4"); // null: no cell
		String workbook = new String(parts.get("xl/workbook.xml"), StandardCharsets.UTF_8);
		assertThat(workbook).contains("name=\"Sheet  with bad chars\"");
		assertThat(XlsxWriter.ref(0, 1)).isEqualTo("A1");
		assertThat(XlsxWriter.ref(27, 12)).isEqualTo("AB12");
	}

	// ---- the export endpoints ----

	@Test
	void issuesAreExportedWithTheBacklogFiltersAndAnyoneWhoCanReadMayExport() throws Exception {
		Fixture f = fixture();
		issue(f, "TASK", "Plain task", 3);
		UUID done = issue(f, "BUG", "=Evil title", 5);
		mockMvc.perform(put("/api/issues/" + done + "/status").header("Authorization", f.owner().bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isOk());

		String all = csv(f.viewer(), "/api/projects/" + f.project() + "/export/issues");
		String[] lines = all.split("\r\n");
		assertThat(lines).hasSize(3);
		assertThat(lines[0]).startsWith("﻿Mã,Loại,Tiêu đề,Trạng thái");
		assertThat(all).contains("Plain task").contains("'=Evil title");
		assertThat(csv(f.owner(), "/api/projects/" + f.project() + "/export/issues?status=DONE").split("\r\n")).hasSize(2);
		assertThat(csv(f.owner(), "/api/projects/" + f.project() + "/export/issues?type=TASK").split("\r\n")).hasSize(2);
		assertThat(csv(f.owner(), "/api/projects/" + f.project() + "/export/issues?createdFrom=2999-01-01T00:00:00Z").split("\r\n")).hasSize(1);
		assertThat(csv(f.owner(), "/api/projects/" + f.project() + "/export/issues?q=plain").split("\r\n")).hasSize(2);

		mockMvc.perform(get("/api/projects/" + f.project() + "/export/issues?format=xlsx").header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", ExportFormat.XLSX.mediaType().toString()))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.matchesPattern("attachment; filename=\"workhub-issues-\\d{4}-\\d{2}-\\d{2}\\.xlsx\"")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mockMvc.perform(get("/api/projects/" + f.project() + "/export/issues?format=docx").header("Authorization", f.owner().bearer()))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FORMAT"));
		// Not a member of the workspace: the project does not exist for them.
		mockMvc.perform(get("/api/projects/" + f.project() + "/export/issues").header("Authorization", TestApi.register(mockMvc).bearer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void sprintAndReportExportsCarryTheNumbers() throws Exception {
		Fixture f = fixture();
		UUID a = issue(f, "TASK", "Finish me", 5);
		UUID b = issue(f, "TASK", "Carry me", 3);
		UUID sprint = UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/sprints")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Sprint 1\",\"startDate\":\"" + LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")) + "\",\"endDate\":\"" + LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(6) + "\"}"))
			.andReturn().getResponse().getContentAsString(), "$.id"));
		mockMvc.perform(post("/api/sprints/" + sprint + "/issues").header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"issueIds\":[\"" + a + "\",\"" + b + "\"],\"includeChildren\":false}")).andExpect(status().isOk());
		mockMvc.perform(post("/api/sprints/" + sprint + "/start").header("Authorization", f.owner().bearer())).andExpect(status().isOk());
		mockMvc.perform(put("/api/issues/" + a + "/status").header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
		mockMvc.perform(post("/api/sprints/" + sprint + "/complete").header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"goalOutcome\":\"PARTIALLY_ACHIEVED\",\"unfinishedTo\":\"BACKLOG\"}")).andExpect(status().isOk());

		String report = csv(f.viewer(), "/api/sprints/" + sprint + "/export");
		assertThat(report).contains("Hoàn thành,").contains("Finish me").contains("Chưa xong,").contains("Carry me");
		String daily = csv(f.viewer(), "/api/sprints/" + sprint + "/export/daily");
		assertThat(daily.split("\r\n")[0]).contains("Ngày,Lý tưởng (điểm)");
		assertThat(daily.split("\r\n").length).isGreaterThanOrEqualTo(2);
		assertThat(csv(f.viewer(), "/api/projects/" + f.project() + "/export/velocity")).contains("Sprint 1,").contains(",8,5,2,1");
		assertThat(csv(f.viewer(), "/api/projects/" + f.project() + "/export/lead-time")).contains("Finish me");
		assertThat(csv(f.viewer(), "/api/projects/" + f.project() + "/export/workload")).contains("Chưa giao");
		assertThat(csv(f.viewer(), "/api/projects/" + f.project() + "/export/bug-trend?weeks=3").split("\r\n")).hasSize(4);
		assertThat(csv(f.viewer(), "/api/projects/" + f.project() + "/export/members")).contains("STAKEHOLDER");
	}

	@Test
	void workspaceAndTeamSummariesListOnlyWhatTheCallerMaySee() throws Exception {
		Fixture f = fixture();
		issue(f, "TASK", "Counted", 2);
		UUID hidden = TestApi.createProject(mockMvc, f.owner(), TestApi.createTeam(mockMvc, f.owner(), f.workspace(), "Secret team"), TestApi.uniqueKey());
		Session outsiderToTeam = TestApi.joinWorkspace(mockMvc, f.owner(), f.workspace(), "MEMBER");

		String mine = csv(f.owner(), "/api/workspaces/" + f.workspace() + "/export/projects");
		assertThat(mine.split("\r\n")).hasSize(3);
		assertThat(mine).contains("Backend").contains("SCRUM");
		// Everyone in the workspace sees every team's project rows (they can view them), but a project of a team
		// they are not on only shows when they are allowed to view it.
		assertThat(csv(f.viewer(), "/api/workspaces/" + f.workspace() + "/export/projects")).contains("Backend");
		assertThat(csv(f.owner(), "/api/teams/" + f.team() + "/export/projects").split("\r\n")).hasSize(2);
		assertThat(csv(outsiderToTeam, "/api/workspaces/" + f.workspace() + "/export/members")).contains("OWNER");
		assertThat(hidden).isNotNull();
	}

	@Test
	void exportsAreRecordedInTheAuditLog() throws Exception {
		Fixture f = fixture();
		csv(f.viewer(), "/api/projects/" + f.project() + "/export/issues?format=xlsx".replace("?format=xlsx", ""));
		mockMvc.perform(get("/api/projects/" + f.project() + "/export/velocity?format=xlsx").header("Authorization", f.owner().bearer())).andExpect(status().isOk());

		mockMvc.perform(get("/api/workspaces/" + f.workspace() + "/audit-log?action=export.project").header("Authorization", f.owner().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].detail").value("format=xlsx"))
			.andExpect(jsonPath("$.items[1].actorId").value(f.viewer().userId().toString()));
	}

	// ---- helpers ----

	private String csv(Session caller, String path) throws Exception {
		ResultActions result = mockMvc.perform(get(path).header("Authorization", caller.bearer())).andExpect(status().isOk());
		return new String(result.andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private UUID issue(Fixture f, String type, String title, int points) throws Exception {
		return UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/projects/" + f.project() + "/issues")
			.header("Authorization", f.owner().bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"storyPoints\":" + points + "}")).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
		Map<String, byte[]> parts = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
				parts.put(e.getName(), zip.readAllBytes());
			}
		}
		return parts;
	}

}
