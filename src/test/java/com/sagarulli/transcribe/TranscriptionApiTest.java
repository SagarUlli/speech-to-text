package com.sagarulli.transcribe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TranscriptionApiTest {
	@Autowired
	MockMvc mvc;

	private static final String BODY = """
			{"title":"Standup","content":"Namaste, let's start the meeting. आज का agenda क्या है?",
			 "language":"Hindi + English","engine":"gemini","durationSeconds":42}""";

	@Test
	void fullCrudLifecycle() throws Exception {
		String loc = mvc.perform(post("/api/transcriptions").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Standup")).andReturn()
				.getResponse().getHeader("Location");

		mvc.perform(get(loc)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("आज का agenda")));

		mvc.perform(get("/api/transcriptions").param("q", "AGENDA")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mvc.perform(put(loc).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("Standup", "Retro")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Retro"));

		mvc.perform(delete(loc)).andExpect(status().isNoContent());
		mvc.perform(get(loc)).andExpect(status().isNotFound());
	}

	@Test
	void rejectsBlankTitle() throws Exception {
		mvc.perform(post("/api/transcriptions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\" \",\"content\":\"x\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.title").exists());
	}

	@Test
	void enginesEndpointReportsAvailability() throws Exception {
		mvc.perform(get("/api/engines")).andExpect(status().isOk()).andExpect(jsonPath("$.engines.length()").value(2));
	}
}
