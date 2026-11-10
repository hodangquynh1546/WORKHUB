package com.workhub.hello;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void greetsByNameWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/hello").param("name", "Quynh"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Hello, Quynh!"));
	}

	@Test
	void defaultsToWorld() throws Exception {
		mockMvc.perform(get("/api/hello"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Hello, World!"));
	}

}
