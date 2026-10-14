package com.bowe.meetstudent.integration.security;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real committed HTTP requests. JDBC barriers force old credentials to be read before the change commits. */
@SpringBootTest(properties = {"spring.jpa.show-sql=false", "app.tokens.cooldown-seconds=0"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PasswordSessionRaceIntegrationTests.DatabaseBarrier.class, com.bowe.meetstudent.support.CapturingMailConfig.class})
class PasswordSessionRaceIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    private final java.util.concurrent.atomic.AtomicReference<java.time.Instant> controlledTime = new java.util.concurrent.atomic.AtomicReference<>();

    @org.junit.jupiter.api.BeforeEach void clockAtSystemTime() {
        controlledTime.set(null);
        org.mockito.Mockito.when(clock.instant()).thenAnswer(call -> {
            var now = controlledTime.get();
            return now == null ? java.time.Instant.now() : now;
        });
    }


    private JsonNode login(String email, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @ParameterizedTest
    @CsvSource({"login, change", "refresh, change", "login, reset", "refresh, reset", "login, email", "refresh, email"})
    void credentialsReadBeforeSensitiveChangeCannotMintASurvivingSession(String operation, String mutation) throws Exception {
        var role = roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(Role.builder().name("ROLE_STUDENT").build()));
        var email = UUID.randomUUID() + "@example.com";
        var user = users.save(UserEntity.builder().email(email).password(encoder.encode("Old-password-123"))
                .role(role).build());
        var before = login(email, "Old-password-123");
        var barrier = new ReadBarrier(operation);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var racing = pool.submit(() -> {
                DatabaseBarrier.active.set(barrier);
                try {
                    var path = operation.equals("login") ? "/api/v1/auth" : "/api/v1/auth/refresh";
                    var payload = operation.equals("login")
                            ? Map.of("username", email, "password", "Old-password-123")
                            : Map.of("refreshToken", before.get("refreshToken").asText());
                    return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(payload))).andReturn().getResponse();
                } finally {
                    DatabaseBarrier.active.remove();
                }
            });
            try {
                assertThat(barrier.read.await(15, TimeUnit.SECONDS)).as("old credential read reached").isTrue();
                changeCredentials(user, before, mutation);
            } finally {
                barrier.resume.countDown();
            }
            var outcome = racing.get(15, TimeUnit.SECONDS);
            assertThat(outcome.getStatus()).as(outcome.getContentAsString()).isEqualTo(401);
            mvc.perform(get("/api/v1/users/id/" + user.getId())
                    .header("Authorization", "Bearer " + before.get("accessToken").asText()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("refreshToken", before.get("refreshToken").asText()))))
                    .andExpect(status().isUnauthorized());
            var after = login(mutation.equals("email") ? "new-" + email : email,
                    mutation.equals("email") ? "Old-password-123" : "New-password-123");
            mvc.perform(get("/api/v1/users/id/" + user.getId())
                    .header("Authorization", "Bearer " + after.get("accessToken").asText()))
                    .andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void refreshWaitingAcrossExpiryRechecksActivityUnderTheAccountLock(boolean activityBeforeExpiry) throws Exception {
        var start = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        controlledTime.set(start);
        var email = UUID.randomUUID() + "@example.com";
        var role = roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(Role.builder().name("ROLE_STUDENT").build()));
        var user = users.save(UserEntity.builder().email(email).password(encoder.encode("Old-password-123")).role(role).build());
        var session = login(email, "Old-password-123");
        controlledTime.set(start.plusSeconds(604799));
        session = refresh(session, 200);
        final var before = session;
        var barrier = new ReadBarrier("refresh");
        try (var pool = Executors.newSingleThreadExecutor()) {
            var racing = pool.submit(() -> {
                DatabaseBarrier.active.set(barrier);
                try {
                    return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("refreshToken", before.get("refreshToken").asText()))))
                            .andReturn().getResponse();
                } finally { DatabaseBarrier.active.remove(); }
            });
            try {
                assertThat(barrier.read.await(15, TimeUnit.SECONDS)).as("refresh owner read before lock").isTrue();
                if (activityBeforeExpiry) {
                    mvc.perform(get("/api/v1/users/id/" + user.getId())
                            .header("Authorization", "Bearer " + before.get("accessToken").asText())
                            .header("X-Session-Activity", "true")).andExpect(status().isOk());
                }
                controlledTime.set(start.plusSeconds(604800));
            } finally { barrier.resume.countDown(); }
            var result = racing.get(15, TimeUnit.SECONDS);
            assertThat(result.getStatus()).isEqualTo(activityBeforeExpiry ? 200 : 401);
            if (activityBeforeExpiry) {
                var after = json.readTree(result.getContentAsString());
                mvc.perform(get("/api/v1/users/id/" + user.getId())
                        .header("Authorization", "Bearer " + after.get("accessToken").asText())).andExpect(status().isOk());
                refresh(before, 401); // rotation cannot leave the old secret usable
                controlledTime.set(start.plusSeconds(1209598));
                after = refresh(after, 200);
                controlledTime.set(start.plusSeconds(1209599));
                refresh(after, 401);
            } else {
                mvc.perform(get("/api/v1/users/id/" + user.getId())
                        .header("Authorization", "Bearer " + before.get("accessToken").asText())
                        .header("X-Session-Activity", "true")).andExpect(status().isUnauthorized());
                refresh(before, 401);
            }
        }
    }

    @org.junit.jupiter.api.Test
    void successfulRequestFinishingAfterTheDeadlineCannotReviveItsSession() throws Exception {
        var start = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        controlledTime.set(start);
        var email = UUID.randomUUID() + "@example.com";
        var role = roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(Role.builder().name("ROLE_STUDENT").build()));
        var user = users.save(UserEntity.builder().email(email).password(encoder.encode("Old-password-123")).role(role).build());
        var session = login(email, "Old-password-123");
        controlledTime.set(start.plusSeconds(604799));
        final var before = refresh(session, 200);
        var barrier = new ReadBarrier("business-read");
        try (var pool = Executors.newSingleThreadExecutor()) {
            var racing = pool.submit(() -> {
                DatabaseBarrier.active.set(barrier);
                try {
                    return mvc.perform(get("/api/v1/users/id/" + user.getId())
                            .header("Authorization", "Bearer " + before.get("accessToken").asText())
                            .header("X-Session-Activity", "true")).andReturn().getResponse();
                } finally { DatabaseBarrier.active.remove(); }
            });
            try {
                assertThat(barrier.read.await(15, TimeUnit.SECONDS)).as("authorized business read reached").isTrue();
                controlledTime.set(start.plusSeconds(604800));
            } finally { barrier.resume.countDown(); }
            assertThat(racing.get(15, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
            refresh(before, 401);
            mvc.perform(get("/api/v1/users/id/" + user.getId())
                    .header("Authorization", "Bearer " + before.get("accessToken").asText())
                    .header("X-Session-Activity", "true")).andExpect(status().isUnauthorized());
        }
    }

    private JsonNode refresh(JsonNode session, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", session.get("refreshToken").asText()))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse();
        return json.readTree(result.getContentAsString());
    }

    @Autowired com.bowe.meetstudent.support.CapturingEmailSender mailbox;

    @ParameterizedTest
    @CsvSource({"email-request, change", "email-resend, change", "email-request, reset", "email-resend, reset", "email-request, email", "email-resend, email"})
    void sensitiveChangeCannotBeFollowedByAnEmailLinkUsingOldCredentials(String operation, String mutation) throws Exception {
        var role = roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(Role.builder().name("ROLE_STUDENT").build()));
        var email = UUID.randomUUID() + "@example.com";
        var newEmail = UUID.randomUUID() + "@example.com";
        var user = users.save(UserEntity.builder().email(email).password(encoder.encode("Old-password-123")).role(role).build());
        var before = login(email, "Old-password-123");
        var auth = "Bearer " + before.get("accessToken").asText();
        var endpoint = "/api/v1/users/" + user.getId() + "/email-change";
        var payload = json.writeValueAsString(Map.of("newEmail", newEmail, "currentPassword", "Old-password-123"));
        if (operation.equals("email-resend")) {
            mvc.perform(post(endpoint).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isAccepted());
        }
        var barrier = new ReadBarrier(operation);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var racing = pool.submit(() -> {
                DatabaseBarrier.active.set(barrier);
                try {
                    return mvc.perform(post(endpoint + (operation.equals("email-resend") ? "/resend" : ""))
                            .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(payload))
                            .andReturn().getResponse();
                } finally { DatabaseBarrier.active.remove(); }
            });
            try {
                assertThat(barrier.read.await(15, TimeUnit.SECONDS)).as("old email request read reached").isTrue();
                changeCredentials(user, before, mutation);
            } finally { barrier.resume.countDown(); }
            var outcome = racing.get(15, TimeUnit.SECONDS);
            assertThat(outcome.getStatus()).as(outcome.getContentAsString()).isIn(400, 409);
            for (var message : mailbox.all().stream().filter(m -> m.to().equals(newEmail)).toList()) {
                var token = com.bowe.meetstudent.support.CapturingEmailSender.extractToken(message);
                mvc.perform(post("/api/v1/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void concurrentConfirmationCannotReuseTheSameResetLink() throws Exception {
        var role = roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(Role.builder().name("ROLE_STUDENT").build()));
        var email = UUID.randomUUID() + "@example.com";
        users.save(UserEntity.builder().email(email).password(encoder.encode("Old-password-123")).role(role).build());
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());
        var token = com.bowe.meetstudent.support.CapturingEmailSender.extractToken(mailbox.last());
        var barrier = new ReadBarrier("reset-confirm");
        try (var pool = Executors.newSingleThreadExecutor()) {
            var racing = pool.submit(() -> {
                DatabaseBarrier.active.set(barrier);
                try {
                    return mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                                    .content(json.writeValueAsString(Map.of("token", token,
                                            "password", "Losing-password-123", "confirmedPassword", "Losing-password-123"))))
                            .andReturn().getResponse();
                } finally { DatabaseBarrier.active.remove(); }
            });
            try {
                assertThat(barrier.read.await(15, TimeUnit.SECONDS)).as("reset token owner read reached").isTrue();
                mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("token", token,
                                        "password", "Winning-password-123", "confirmedPassword", "Winning-password-123"))))
                        .andExpect(status().isNoContent());
            } finally { barrier.resume.countDown(); }
            assertThat(racing.get(15, TimeUnit.SECONDS).getStatus()).isEqualTo(400);
            login(email, "Winning-password-123");
            mvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", email, "password", "Losing-password-123"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void changeCredentials(UserEntity user, JsonNode session, String mutation) throws Exception {
        if (mutation.equals("email")) {
            mvc.perform(post("/api/v1/users/" + user.getId() + "/email-change")
                            .header("Authorization", "Bearer " + session.get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                    "newEmail", "new-" + user.getEmail(), "currentPassword", "Old-password-123"))))
                    .andExpect(status().isAccepted());
            var token = com.bowe.meetstudent.support.CapturingEmailSender.extractToken(mailbox.last());
            mvc.perform(post("/api/v1/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("token", token))))
                    .andExpect(status().isNoContent());
        } else if (mutation.equals("reset")) {
            mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", user.getEmail()))))
                    .andExpect(status().isAccepted());
            var token = com.bowe.meetstudent.support.CapturingEmailSender.extractToken(mailbox.last());
            mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("token", token,
                                    "password", "New-password-123", "confirmedPassword", "New-password-123"))))
                    .andExpect(status().isNoContent());
        } else {
            mvc.perform(patch("/api/v1/users/" + user.getId())
                            .header("Authorization", "Bearer " + session.get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                    "currentPassword", "Old-password-123", "password", "New-password-123"))))
                    .andExpect(status().isOk());
        }
    }

    private static class ReadBarrier {
        final String operation;
        final CountDownLatch read = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        ReadBarrier(String operation) { this.operation = operation; }
        void afterQuery(String sql) throws InterruptedException {
            boolean matches = switch (operation) {
                case "login" -> sql.contains("from users ") && sql.contains("upper(");
                case "refresh" -> sql.contains("from refresh_tokens ");
                case "reset-confirm" -> sql.contains("from user_tokens ") && sql.contains(".token_hash=?");
                case "business-read", "email-request" -> sql.contains("from users ") && sql.contains(".password,");
                case "email-resend" -> sql.contains("from user_tokens ") && sql.contains("consumed_at is null");
                default -> false;
            };
            if (matches && read.getCount() > 0) {
                read.countDown();
                if (!resume.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Credential barrier timed out");
            }
        }
    }

    /** Wrap only the external JDBC boundary; SQL still executes against the actual test database. */
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseBarrier {
        static final ThreadLocal<ReadBarrier> active = new ThreadLocal<>();
        @Bean static BeanPostProcessor credentialReadBarrier() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource source)) return bean;
                    return wrap(DataSource.class, source, null);
                }
            };
        }
        private static Object wrap(Class<?> type, Object target, String sql) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                Object result;
                try { result = method.invoke(target, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
                if (result instanceof Connection) return wrap(Connection.class, result, null);
                if (result instanceof PreparedStatement && args != null && args[0] instanceof String query) {
                    return wrap(PreparedStatement.class, result, query);
                }
                if (method.getName().equals("executeQuery") && active.get() != null) active.get().afterQuery(sql);
                return result;
            });
        }
    }
}
