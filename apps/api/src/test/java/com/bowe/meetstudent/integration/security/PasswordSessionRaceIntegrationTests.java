package com.bowe.meetstudent.integration.security;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PasswordSessionRaceIntegrationTests.DatabaseBarrier.class)
class PasswordSessionRaceIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    private JsonNode login(String email, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "refresh"})
    void credentialsReadBeforePasswordChangeCannotMintASurvivingSession(String operation) throws Exception {
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
                mvc.perform(patch("/api/v1/users/" + user.getId())
                        .header("Authorization", "Bearer " + before.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "currentPassword", "Old-password-123", "password", "New-password-123"))))
                        .andExpect(status().isOk());
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
            var after = login(email, "New-password-123");
            mvc.perform(get("/api/v1/users/id/" + user.getId())
                    .header("Authorization", "Bearer " + after.get("accessToken").asText()))
                    .andExpect(status().isOk());
        }
    }

    private static class ReadBarrier {
        final String operation;
        final CountDownLatch read = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        ReadBarrier(String operation) { this.operation = operation; }
        void afterQuery(String sql) throws InterruptedException {
            boolean matches = operation.equals("login")
                    ? sql.contains("from users ") && sql.contains("upper(")
                    : sql.contains("from refresh_tokens ");
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
