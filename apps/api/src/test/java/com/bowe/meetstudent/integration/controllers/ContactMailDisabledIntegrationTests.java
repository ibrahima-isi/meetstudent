package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.repositories.ContactMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default test configuration: MAIL_ENABLED=false, so no mail leaves but the message is kept and the answer is unchanged. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactMailDisabledIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void theMessageIsStoredAndTheAnswerStays202WhileMailIsDisabled() throws Exception {
        repository.deleteAll();
        mockMvc.perform(post("/api/v1/contact")
                        .with(r -> {
                            r.setRemoteAddr("192.0.2.77");
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        assertThat(repository.count()).isEqualTo(1);
    }
}
