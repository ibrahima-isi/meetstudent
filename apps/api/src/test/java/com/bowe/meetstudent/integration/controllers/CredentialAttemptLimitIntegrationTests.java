package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brute-force limits: per-account progressive lockout on sign-in (lower-cased email, known or not) and per-user
 * lockout of the current-password checks (email change, account deletion, password change).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CredentialAttemptLimitIntegrationTests {

    private static final String PASSWORD = "Right-password-123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @MockitoBean Clock clock;

    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach
    void controlledClock() {
        now.set(Instant.now());
        when(clock.instant()).thenAnswer(call -> now.get());
    }

    private void advance(Duration duration) {
        now.updateAndGet(t -> t.plus(duration));
    }

    private UserEntity account(String role) {
        var r = roles.findByName(role).orElseGet(() -> roles.save(Role.builder().name(role).build()));
        return users.save(UserEntity.builder().email(UUID.randomUUID() + "@example.com").firstname("Awa")
                .password(encoder.encode(PASSWORD)).role(r).build());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", email, "password", password)))).andReturn();
    }

    private void wrongLogins(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(login(email, "Wrong-password-" + i).getResponse().getStatus()).isEqualTo(401);
        }
    }

    private void loginOk(String email) throws Exception {
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    /** Status, Retry-After and the body without its timestamp: what a client can compare. */
    private String visible(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).remove("timestamp");
        return result.getResponse().getStatus() + " " + result.getResponse().getHeader(HttpHeaders.RETRY_AFTER) + " " + body;
    }

    // ---- sign-in -------------------------------------------------------------------------------

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
        var user = account("ROLE_STUDENT");
        wrongLogins(user.getEmail(), 5);
        var locked = login(user.getEmail(), PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        assertThat(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        var body = json.readTree(locked.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("message").asText()).isEqualTo("Trop de tentatives, réessayez plus tard");
        assertThat(locked.getResponse().getContentAsString()).doesNotContain(user.getEmail()).doesNotContainIgnoringCase("accessToken");
    }

    @Test
    void anUnknownEmailIsLockedExactlyLikeAKnownOne() throws Exception {
        var user = account("ROLE_STUDENT");
        var unknown = "nobody-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            assertThat(visible(login(unknown, "Wrong-" + i))).isEqualTo(visible(login(user.getEmail(), "Wrong-" + i)));
        }
        var lockedUnknown = login(unknown, PASSWORD);
        var lockedKnown = login(user.getEmail(), PASSWORD);
        assertThat(lockedKnown.getResponse().getStatus()).isEqualTo(429);
        assertThat(visible(lockedUnknown)).isEqualTo(visible(lockedKnown));
    }

    @Test
    void afterTheLockTheRightPasswordWorksAndResetsTheCounter() throws Exception {
        var user = account("ROLE_STUDENT");
        wrongLogins(user.getEmail(), 5);
        advance(Duration.ofSeconds(59));
        assertThat(login(user.getEmail(), PASSWORD).getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        advance(Duration.ofSeconds(1));
        loginOk(user.getEmail());
        wrongLogins(user.getEmail(), 4);
        loginOk(user.getEmail());
    }

    @Test
    void aSuccessBeforeFiveFailuresResetsTheCounter() throws Exception {
        var user = account("ROLE_STUDENT");
        wrongLogins(user.getEmail(), 4);
        loginOk(user.getEmail());
        wrongLogins(user.getEmail(), 4);
        loginOk(user.getEmail());
    }

    @Test
    void theSecondLockLastsFiveMinutesAndTheThirdFifteen() throws Exception {
        var user = account("ROLE_STUDENT");
        wrongLogins(user.getEmail(), 5);
        advance(Duration.ofMinutes(1));
        wrongLogins(user.getEmail(), 5);
        assertThat(login(user.getEmail(), PASSWORD).getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("300");
        advance(Duration.ofMinutes(5));
        wrongLogins(user.getEmail(), 5);
        assertThat(login(user.getEmail(), PASSWORD).getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
    }

    @Test
    void otherAccountsAreUnaffectedAndCaseVariantsShareTheLock() throws Exception {
        var victim = account("ROLE_STUDENT");
        var other = account("ROLE_STUDENT");
        wrongLogins(victim.getEmail().toUpperCase(), 5);
        assertThat(login(victim.getEmail(), PASSWORD).getResponse().getStatus()).isEqualTo(429);
        loginOk(other.getEmail());
    }

    @Test
    void adminsAreLockedTheSameWay() throws Exception {
        var admin = account("ROLE_ADMIN");
        wrongLogins(admin.getEmail(), 5);
        assertThat(login(admin.getEmail(), PASSWORD).getResponse().getStatus()).isEqualTo(429);
    }

    // ---- current password ----------------------------------------------------------------------

    private int perform(MockHttpServletRequestBuilder request, UserEntity user, Object body) throws Exception {
        return mvc.perform(request.with(TestDataUtil.mockUser(user.getId(), user.getRole().getName()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }

    private int emailChange(UserEntity user, String currentPassword) throws Exception {
        return perform(post("/api/v1/users/" + user.getId() + "/email-change"), user,
                Map.of("newEmail", UUID.randomUUID() + "@example.com", "currentPassword", currentPassword));
    }

    @Test
    void emailChangeLocksAfterFiveWrongCurrentPasswords() throws Exception {
        var user = account("ROLE_STUDENT");
        for (int i = 0; i < 5; i++) {
            assertThat(emailChange(user, "Wrong-" + i)).isEqualTo(400);
        }
        var locked = mvc.perform(post("/api/v1/users/" + user.getId() + "/email-change")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("newEmail", "fresh@example.com", "currentPassword", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.message").value("Trop de tentatives, réessayez plus tard"))
                .andReturn();
        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        // The sign-in is a separate budget.
        loginOk(user.getEmail());
        // After the window the right password passes the check again (mail is off in tests, hence 503).
        advance(Duration.ofMinutes(1));
        assertThat(emailChange(user, PASSWORD)).isEqualTo(503);
    }

    @Test
    void accountDeletionLocksAfterFiveWrongCurrentPasswordsAndKeepsTheAccount() throws Exception {
        var user = account("ROLE_STUDENT");
        for (int i = 0; i < 5; i++) {
            assertThat(perform(delete("/api/v1/users/" + user.getId()), user, Map.of("currentPassword", "Wrong-" + i))).isEqualTo(400);
        }
        assertThat(perform(delete("/api/v1/users/" + user.getId()), user, Map.of("currentPassword", PASSWORD))).isEqualTo(429);
        assertThat(users.existsById(user.getId())).isTrue();
        advance(Duration.ofMinutes(1));
        assertThat(perform(delete("/api/v1/users/" + user.getId()), user, Map.of("currentPassword", PASSWORD))).isEqualTo(204);
        assertThat(users.existsById(user.getId())).isFalse();
    }

    @Test
    void passwordChangeLocksAfterFiveWrongCurrentPasswords() throws Exception {
        var user = account("ROLE_STUDENT");
        for (int i = 0; i < 5; i++) {
            assertThat(perform(patch("/api/v1/users/" + user.getId()), user,
                    Map.of("password", "New-password-123", "currentPassword", "Wrong-" + i))).isEqualTo(400);
        }
        assertThat(perform(patch("/api/v1/users/" + user.getId()), user,
                Map.of("password", "New-password-123", "currentPassword", PASSWORD))).isEqualTo(429);
        assertThat(encoder.matches(PASSWORD, users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void aRightCurrentPasswordBeforeFiveFailuresResetsTheCounterAndOtherUsersAreUnaffected() throws Exception {
        var user = account("ROLE_STUDENT");
        var other = account("ROLE_STUDENT");
        for (int i = 0; i < 4; i++) {
            assertThat(emailChange(user, "Wrong-" + i)).isEqualTo(400);
        }
        assertThat(emailChange(user, PASSWORD)).isEqualTo(503);
        for (int i = 0; i < 4; i++) {
            assertThat(emailChange(user, "Wrong-" + i)).isEqualTo(400);
        }
        assertThat(emailChange(user, PASSWORD)).isEqualTo(503);
        for (int i = 0; i < 5; i++) {
            assertThat(emailChange(user, "Wrong-" + i)).isEqualTo(400);
        }
        assertThat(emailChange(user, PASSWORD)).isEqualTo(429);
        assertThat(emailChange(other, PASSWORD)).isEqualTo(503);
    }

    @Test
    void anAdministratorEditingAnotherAccountIsNotAskedForAPasswordAndNotLimited() throws Exception {
        var admin = account("ROLE_ADMIN");
        var student = account("ROLE_STUDENT");
        for (int i = 0; i < 5; i++) {
            assertThat(perform(patch("/api/v1/users/" + admin.getId()), admin,
                    Map.of("password", "New-password-123", "currentPassword", "Wrong-" + i))).isEqualTo(400);
        }
        assertThat(perform(patch("/api/v1/users/" + admin.getId()), admin,
                Map.of("password", "New-password-123", "currentPassword", PASSWORD))).isEqualTo(429);
        assertThat(perform(patch("/api/v1/users/" + student.getId()), admin, Map.of("password", "New-password-123"))).isEqualTo(200);
    }
}
