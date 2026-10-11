package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.repositories.UserTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default test configuration: email delivery is disabled, the endpoint must still answer exactly the same. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetMailDisabledIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserTokenRepository userTokenRepository;

    @Test
    void forgotPasswordStillAnswers202WithTheNeutralBodyAndCreatesNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("{\"status\":\"accepted\"}"));

        assertThat(userTokenRepository.count()).isZero();
    }
}
