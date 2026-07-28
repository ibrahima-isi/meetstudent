package com.bowe.meetstudent.integration.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS preflight rules. The browser sends an OPTIONS preflight before any request
 * carrying a non-simple header, so a header missing from the allowlist makes the
 * real request impossible from the SPA — even though server-to-server calls
 * (which never preflight) work fine.
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTests {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @Autowired
    MockMvc mockMvc;

    @Test
    void preflightAllowsIdempotencyKeyHeaderOnMediaUpload() throws Exception {
        mockMvc.perform(options("/api/v1/media")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("idempotency-key")));
    }

    @Test
    void preflightAllowsAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/v1/media")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")));
    }

    /**
     * Opposite of the rule above: the allowlist must stay an allowlist. This fails
     * if someone "fixes" a blocked header by widening the config to "*".
     */
    @Test
    void preflightRejectsHeaderOutsideTheAllowlist() throws Exception {
        mockMvc.perform(options("/api/v1/media")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-evil-header"))
                .andExpect(status().isForbidden());
    }

    /** Origin policy must remain untouched by the header change. */
    @Test
    void preflightRejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/media")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "idempotency-key"))
                .andExpect(status().isForbidden());
    }
}
