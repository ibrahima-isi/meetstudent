package com.bowe.meetstudent.integration.config;

import com.bowe.meetstudent.MeetstudentApplication;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A complete context shutdown/restart, preserving the database and the original bootstrap environment. */
class AdminBootstrapRestartIntegrationTests {
    private final ObjectMapper json = new ObjectMapper();
    private static final String EMAIL = "owner@meetstudent.example";
    private static final String INITIAL_PASSWORD = "S3cure-admin-pass";
    private static final String RESET_PASSWORD = "Recovered-admin-pass";

    @Test
    void resetAdminPasswordAndRevocationsSurviveApplicationRestart() throws Exception {
        var database = "restart_" + UUID.randomUUID();
        JsonNode oldSession;
        String resetToken;
        String emailChangeToken;
        int userId;
        try (var first = start(database, true)) {
            var mvc = mvc(first);
            oldSession = postJson(mvc, "/api/v1/auth", Map.of("username", EMAIL, "password", INITIAL_PASSWORD), 200);
            userId = Integer.parseInt(com.auth0.jwt.JWT.decode(oldSession.get("accessToken").asText()).getSubject());
            var mail = first.getBean(CapturingEmailSender.class);
            mvc.perform(post("/api/v1/users/" + userId + "/email-change")
                            .header("Authorization", "Bearer " + oldSession.get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("newEmail", "unwanted@example.com", "currentPassword", INITIAL_PASSWORD))))
                    .andExpect(status().isAccepted());
            emailChangeToken = nextEmailToken(mail);
            postJson(mvc, "/api/v1/auth/password-reset/request", Map.of("email", EMAIL, "lang", "fr"), 202);
            resetToken = nextEmailToken(mail);
            postJson(mvc, "/api/v1/auth/password-reset/confirm", Map.of("token", resetToken,
                    "password", RESET_PASSWORD, "confirmedPassword", RESET_PASSWORD), 204);
        }
        try (var restarted = start(database, false)) {
            var mvc = mvc(restarted);
            postJson(mvc, "/api/v1/auth", Map.of("username", EMAIL, "password", INITIAL_PASSWORD), 401);
            var recovered = postJson(mvc, "/api/v1/auth", Map.of("username", EMAIL, "password", RESET_PASSWORD), 200);
            mvc.perform(get("/api/v1/users/id/" + userId)
                            .header("Authorization", "Bearer " + oldSession.get("accessToken").asText()))
                    .andExpect(status().isUnauthorized());
            postJson(mvc, "/api/v1/auth/refresh", Map.of("refreshToken", oldSession.get("refreshToken").asText()), 401);
            postJson(mvc, "/api/v1/auth/password-reset/confirm", Map.of("token", resetToken,
                    "password", "unwanted-password", "confirmedPassword", "unwanted-password"), 400);
            postJson(mvc, "/api/v1/auth/email-change/confirm", Map.of("token", emailChangeToken), 400);
            mvc.perform(get("/api/v1/users/id/" + userId)
                            .header("Authorization", "Bearer " + recovered.get("accessToken").asText()))
                    .andExpect(status().isOk());
        }
    }

    private ConfigurableApplicationContext start(String database, boolean initial) {
        return new SpringApplicationBuilder(MeetstudentApplication.class, CapturingMailConfig.class)
                .profiles("prod")
                .run("--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                        "--spring.datasource.username=sa", "--spring.datasource.password=sa",
                        "--spring.jpa.hibernate.ddl-auto=" + (initial ? "create" : "validate"),
                        "--spring.flyway.enabled=false", "--spring.jpa.defer-datasource-initialization=true",
                        "--spring.sql.init.mode=" + (initial ? "always" : "never"),
                        "--spring.sql.init.data-locations=classpath:db/test-admin-seed.sql",
                        "--security.jwt.secret-key=ThisIsASecretKeyForProdProfileTesting!12345",
                        "--app.cors.allowed-origins=https://app.example.com",
                        "--app.admin.email=" + EMAIL, "--app.admin.password=" + INITIAL_PASSWORD,
                        "--app.mail.enabled=false", "--app.mail.dev-log=false",
                        "--file.upload-dir=target/test-uploads", "--file.private-dir=target/test-private");
    }

    private MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
    }

    private JsonNode postJson(MockMvc mvc, String path, Map<String, String> body, int expectedStatus) throws Exception {
        var response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : json.readTree(response);
    }

    private String nextEmailToken(CapturingEmailSender mail) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mail.last() != null);
        var token = CapturingEmailSender.extractToken(mail.last());
        mail.clear();
        return token;
    }
}
