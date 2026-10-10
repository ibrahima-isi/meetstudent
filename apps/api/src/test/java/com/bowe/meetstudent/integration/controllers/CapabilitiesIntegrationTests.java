package com.bowe.meetstudent.integration.controllers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CapabilitiesIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCallerSeesEmailDisabledByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/auth/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));
    }

    @Test
    void aHostileHostHeaderChangesNothing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/capabilities").header("Host", "evil.example").header("X-Forwarded-Host", "evil.example"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"emailEnabled\":false}", true));
    }

    @Nested
    @TestPropertySource(properties = {
            "app.mail.enabled=true",
            "app.mail.from=no-reply@example.com",
            "spring.mail.host=smtp.invalid.example"
    })
    class WhenMailIsConfigured {

        @Autowired
        private MockMvc configuredMockMvc;

        @Test
        void reportsEmailEnabled() throws Exception {
            configuredMockMvc.perform(get("/api/v1/auth/capabilities"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emailEnabled").value(true));
        }
    }
}
