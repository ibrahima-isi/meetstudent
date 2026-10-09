package com.bowe.meetstudent.integration.config;

import com.bowe.meetstudent.config.AdminBootstrapRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the real application under the {@code prod} profile (with H2 and a SQL stand-in for the
 * Flyway seed) and checks the end-to-end outcome: the env admin can log in, the seeded default
 * credentials cannot.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prodtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.data-locations=classpath:db/test-admin-seed.sql",
        "security.jwt.secret-key=ThisIsASecretKeyForProdProfileTesting!12345",
        "app.cors.allowed-origins=https://app.example.com",
        "app.admin.email=owner@meetstudent.example",
        "app.admin.password=S3cure-admin-pass",
        "file.upload-dir=target/test-uploads",
        "file.private-dir=target/test-private"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class AdminBootstrapProdProfileIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    private void login(String username, String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void runnerIsRegisteredUnderProd() {
        assertNotNull(context.getBean(AdminBootstrapRunner.class));
    }

    @Test
    void envAdminCanLogIn_andReceivesTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner@meetstudent.example\",\"password\":\"S3cure-admin-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void seededDefaultCredentialsNoLongerWork() throws Exception {
        login("admin@meetstudent.com", "password", 401);
        login("owner@meetstudent.example", "password", 401);
        assertEquals(1, context.getBeansOfType(AdminBootstrapRunner.class).size());
    }
}
