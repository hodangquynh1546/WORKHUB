package com.workhub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Documents owned by SmartSpec are read-only for people; only the integration (shared key) may change them. */
@SpringBootTest(properties = "workhub.integration.webhook-secret=managed-secret")
@AutoConfigureMockMvc
class ManagedDocumentTests {

	@Autowired
	private MockMvc mockMvc;

	private static final String KEY = "X-Integration-Key";

	@Test
	void onlyTheIntegrationCanTagOrChangeManagedDocuments() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Team");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		String create = "{\"title\":\"ERD\",\"type\":\"TECHNICAL_DESIGN\",\"content\":\"v1\",\"externalRef\":\"smartspec:design/p/erd\"}";

		// a person cannot create a managed document
		mockMvc.perform(post("/api/projects/" + project + "/documents").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("DOCUMENT_MANAGED"));

		// the integration can
		String doc = JsonPath.read(mockMvc.perform(post("/api/projects/" + project + "/documents")
			.header("Authorization", owner.bearer()).header(KEY, "managed-secret").contentType(MediaType.APPLICATION_JSON).content(create))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.managed").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// people see it as read-only
		mockMvc.perform(get("/api/documents/" + doc).header("Authorization", owner.bearer())).andExpect(status().isOk())
			.andExpect(jsonPath("$.canEdit").value(false)).andExpect(jsonPath("$.canDelete").value(false))
			.andExpect(jsonPath("$.managed").value(true));
		String update = "{\"title\":\"ERD\",\"type\":\"TECHNICAL_DESIGN\",\"content\":\"hand edit\",\"baseVersion\":1}";
		mockMvc.perform(put("/api/documents/" + doc).header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(update)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DOCUMENT_MANAGED"));
		mockMvc.perform(post("/api/documents/" + doc + "/archive").header("Authorization", owner.bearer())).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/documents/" + doc).header("Authorization", owner.bearer())).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/documents/" + doc + "/collab/meta").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"type\":\"OTHER\",\"folderId\":null}")).andExpect(status().isForbidden());
		// a wrong key does not unlock it
		mockMvc.perform(put("/api/documents/" + doc).header("Authorization", owner.bearer()).header(KEY, "guess")
			.contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isForbidden());

		// the integration can update and archive it; the content really changed
		mockMvc.perform(put("/api/documents/" + doc).header("Authorization", owner.bearer()).header(KEY, "managed-secret")
			.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"ERD\",\"type\":\"TECHNICAL_DESIGN\",\"content\":\"v2\",\"baseVersion\":1}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.content").value("v2"));
		mockMvc.perform(post("/api/documents/" + doc + "/archive").header("Authorization", owner.bearer()).header(KEY, "managed-secret"))
			.andExpect(status().isOk());
		assertThat(doc).isNotBlank();
	}

	@Test
	void ordinaryDocumentsAreUnaffected() throws Exception {
		Session owner = TestApi.register(mockMvc);
		UUID ws = TestApi.createWorkspace(mockMvc, owner, "Org");
		UUID team = TestApi.createTeam(mockMvc, owner, ws, "Team");
		UUID project = TestApi.createProject(mockMvc, owner, team, TestApi.uniqueKey());
		String doc = JsonPath.read(mockMvc.perform(post("/api/projects/" + project + "/documents").header("Authorization", owner.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Notes\",\"type\":\"OTHER\",\"content\":\"a\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.managed").value(false)).andReturn().getResponse().getContentAsString(), "$.id");
		mockMvc.perform(put("/api/documents/" + doc).header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Notes\",\"type\":\"OTHER\",\"content\":\"b\",\"baseVersion\":1}")).andExpect(status().isOk());
	}

}
