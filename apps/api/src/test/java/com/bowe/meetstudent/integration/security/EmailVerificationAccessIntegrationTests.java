package com.bowe.meetstudent.integration.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationAccessIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void confirmIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("Lien invalide ou expiré"));
    }

    @Test
    void resendRequiresAuthenticationAndAnswersWithAnErrorResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").exists());
    }

    @Test
    void confirmDoesNotAcceptGet() throws Exception {
        // A mail scanner prefetching the link with GET must not be able to consume a token.
        mockMvc.perform(get("/api/v1/auth/email-verification/confirm"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void resendDoesNotAcceptGet() throws Exception {
        mockMvc.perform(get("/api/v1/auth/email-verification/resend"))
                .andExpect(status().isMethodNotAllowed());
    }
}
