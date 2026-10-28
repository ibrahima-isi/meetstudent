package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh-token reuse detection, through real HTTP requests whose transactions COMMIT (deliberately not
 * {@code @Transactional}): a revocation that the failing request rolled back would look fine in a test transaction.
 * Only the immediately previous secret of a session is recognised; replaying it ends that session (all its
 * credentials), except during a short grace window after the rotation (benign concurrent refreshes).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenReuseIntegrationTests {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired RefreshTokenRepository sessions;
    @MockitoBean Clock clock;

    private final AtomicReference<Instant> now = new AtomicReference<>();
    private Instant start;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        now.set(start);
        when(clock.instant()).thenAnswer(call -> now.get());
        var role = roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(Role.builder().name("ROLE_STUDENT").build()));
        user = users.save(UserEntity.builder().email(UUID.randomUUID() + "@example.com")
                .password(passwords.encode("password123")).role(role).build());
    }

    @Test
    void theCurrentSecretRotatesAgainAndAgain() throws Exception {
        var tokens = login();
        for (int i = 1; i <= 3; i++) {
            now.set(start.plusSeconds(60L * i));
            var next = refreshOk(tokens);
            assertThat(next.get("refreshToken").asText()).isNotEqualTo(tokens.get("refreshToken").asText());
            accessAllowed(next);
            tokens = next;
        }
    }

    @Test
    void replayingThePreviousSecretAfterTheGraceWindowEndsThatSessionOnlyAndLooksLikeAnUnknownToken() throws Exception {
        var laptop = login();
        var phone = login();
        var rotated = refreshOk(laptop);

        now.set(start.plusSeconds(10));
        var replay = refresh(laptop.get("refreshToken").asText());
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertSameRefusal(replay, refresh("does-not-exist"));

        // The whole session is gone, committed: its current secret and its access token are refused too.
        assertThat(refresh(rotated.get("refreshToken").asText()).getResponse().getStatus()).isEqualTo(401);
        accessRefused(rotated);
        assertThat(sessions.findByToken(AuthIntegrationTests.sha256Hex(rotated.get("refreshToken").asText()))).isEmpty();
        // The other device of the same user is untouched.
        accessAllowed(phone);
        accessAllowed(refreshOk(phone));
    }

    @Test
    void replayingThePreviousSecretInsideTheGraceWindowIsRefusedWithoutEndingTheSession() throws Exception {
        var tokens = login();
        var rotated = refreshOk(tokens);

        now.set(start.plusSeconds(9));
        var replay = refresh(tokens.get("refreshToken").asText());
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertSameRefusal(replay, refresh("does-not-exist"));

        accessAllowed(rotated);
        accessAllowed(refreshOk(rotated));
    }

    @Test
    void anUnknownSecretEndsNothing() throws Exception {
        var laptop = login();
        var phone = login();

        assertThat(refresh("does-not-exist").getResponse().getStatus()).isEqualTo(401);

        accessAllowed(refreshOk(laptop));
        accessAllowed(refreshOk(phone));
    }

    @Test
    void onlyTheImmediatelyPreviousSecretIsRecognisedOlderOnesAreUnknown() throws Exception {
        var first = login();
        var second = refreshOk(first);
        now.set(start.plusSeconds(60));
        var third = refreshOk(second);

        now.set(start.plusSeconds(120));
        assertThat(refresh(first.get("refreshToken").asText()).getResponse().getStatus()).isEqualTo(401);

        accessAllowed(refreshOk(third));
    }

    @Test
    void logoutWithTheCurrentSecretStillEndsARotatedSessionAndTheOldSecretThenRevokesNothing() throws Exception {
        var laptop = login();
        var phone = login();
        var rotated = refreshOk(laptop);

        http.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(body(rotated.get("refreshToken").asText())))
                .andExpect(status().isNoContent());
        accessRefused(rotated);

        now.set(start.plusSeconds(60));
        assertThat(refresh(laptop.get("refreshToken").asText()).getResponse().getStatus()).isEqualTo(401);
        accessAllowed(refreshOk(phone));
    }

    private JsonNode login() throws Exception {
        return json.readTree(http.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user.getEmail(), "password", "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MvcResult refresh(String secret) throws Exception {
        return http.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(secret)))
                .andReturn();
    }

    private JsonNode refreshOk(JsonNode tokens) throws Exception {
        var result = refresh(tokens.get("refreshToken").asText());
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String body(String secret) throws Exception {
        return json.writeValueAsString(Map.of("refreshToken", secret));
    }

    private void accessAllowed(JsonNode tokens) throws Exception {
        http.perform(get("/api/v1/users/id/" + user.getId())
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())).andExpect(status().isOk());
    }

    private void accessRefused(JsonNode tokens) throws Exception {
        http.perform(get("/api/v1/users/id/" + user.getId())
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())).andExpect(status().isUnauthorized());
    }

    /** Same status and body (timestamp aside): the caller cannot tell a detected replay from an unknown token. */
    private void assertSameRefusal(MvcResult replay, MvcResult unknown) throws Exception {
        assertThat(replay.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus());
        var a = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(replay.getResponse().getContentAsString());
        var b = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(unknown.getResponse().getContentAsString());
        a.remove("timestamp");
        b.remove("timestamp");
        assertThat(a).isEqualTo(b);
        assertThat(replay.getResponse().getHeaderNames()).containsExactlyInAnyOrderElementsOf(unknown.getResponse().getHeaderNames());
    }
}
