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

    /** Postgres (and H2) compare with upper(): 'ſ' (long s) finds 's', 'ı' (dotless i) finds 'i'. */
    @Test
    void unicodeSpellingsThatFindTheAccountShareItsLock() throws Exception {
        var user = account("ROLE_STUDENT");
        user.setEmail("student-" + UUID.randomUUID() + "@example.com");
        user = users.save(user);
        var longS = user.getEmail().replaceFirst("s", "ſ");
        assertThat(login(longS, PASSWORD).getResponse().getStatus()).as("the database finds the account").isEqualTo(200);
        wrongLogins(longS, 3);
        wrongLogins(user.getEmail().replace("s", "ſ"), 2);
        assertThat(login(user.getEmail(), PASSWORD).getResponse().getStatus()).isEqualTo(429);

        var other = account("ROLE_STUDENT");
        other.setEmail("id-" + UUID.randomUUID() + "@example.com");
        other = users.save(other);
        wrongLogins(other.getEmail().replaceFirst("i", "ı"), 4);
        wrongLogins(other.getEmail().toUpperCase(), 1);
        assertThat(login(other.getEmail(), PASSWORD).getResponse().getStatus()).isEqualTo(429);
    }

    /** Fullwidth 'ａ' does not find the account but folds to 'A' under NFKC: the lock must not tell known from unknown. */
    @Test
    void aSpellingTheDatabaseDoesNotFoldLocksKnownAndUnknownAddressesAlike() throws Exception {
        var user = account("ROLE_STUDENT");
        user.setEmail("alice-" + UUID.randomUUID() + "@example.com");
        user = users.save(user);
        var unknown = "alice-" + UUID.randomUUID() + "@example.com";
        assertThat(login(user.getEmail().replaceFirst("a", "ａ"), PASSWORD).getResponse().getStatus())
                .as("the database does not find the fullwidth spelling").isEqualTo(401);
        wrongLogins(user.getEmail().replaceFirst("a", "ａ"), 4);
        wrongLogins(unknown.replaceFirst("a", "ａ"), 5);
        var known = login(user.getEmail(), PASSWORD);
        var other = login(unknown, PASSWORD);
        assertThat(known.getResponse().getStatus()).isEqualTo(429);
        assertThat(visible(known).replace(user.getEmail(), "")).isEqualTo(visible(other).replace(unknown, ""));
    }

    /**
     * Review scenario: 3 failures with the plain address, 2 with a fullwidth spelling, a minute's wait, then more
     * failures. With a separate per-account counter a known address locked earlier than an unknown one; a single
     * normalised key gives both the same sequence of answers, wrong passwords only.
     */
    @Test
    void knownAndUnknownAddressesGiveIdenticalSequencesUnderMixedSpellings() throws Exception {
        var user = account("ROLE_STUDENT");
        user.setEmail("alice-" + UUID.randomUUID() + "@example.com");
        user = users.save(user);
        var known = user.getEmail();
        var unknown = "alice-" + UUID.randomUUID() + "@example.com";
        var knownAnswers = new java.util.ArrayList<String>();
        var unknownAnswers = new java.util.ArrayList<String>();
        for (var round : java.util.List.of(0, 1)) {
            for (int i = 0; i < 3; i++) {
                knownAnswers.add(visible(login(known, "Wrong-" + i)));
                unknownAnswers.add(visible(login(unknown, "Wrong-" + i)));
            }
            for (int i = 0; i < 2; i++) {
                knownAnswers.add(visible(login(known.replaceFirst("a", "ａ"), "Wrong-" + i)));
                unknownAnswers.add(visible(login(unknown.replaceFirst("a", "ａ"), "Wrong-" + i)));
            }
            if (round == 0) {
                advance(Duration.ofSeconds(61));
            }
        }
        knownAnswers.add(visible(login(known, PASSWORD)));
        unknownAnswers.add(visible(login(unknown, PASSWORD)));
        assertThat(knownAnswers).isEqualTo(unknownAnswers);
        assertThat(knownAnswers).extracting(a -> a.substring(0, 3))
                .containsExactly("401", "401", "401", "401", "401", "401", "401", "401", "401", "401", "429");
        assertThat(knownAnswers.get(10)).startsWith("429 300 ");
    }

    @Test
    void unicodeSpellingsOfAnUnknownEmailShareItsLockToo() throws Exception {
        var unknown = "student-" + UUID.randomUUID() + "@example.com";
        wrongLogins(unknown.replaceFirst("s", "ſ"), 2);
        wrongLogins(unknown.replace("i", "ı"), 2);
        wrongLogins(unknown, 1);
        assertThat(login(unknown.toUpperCase(), PASSWORD).getResponse().getStatus()).isEqualTo(429);
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
