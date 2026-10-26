package com.bowe.meetstudent.integration.controllers;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionInactivityIntegrationTests {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired com.bowe.meetstudent.repositories.RefreshTokenRepository sessions;
    @Autowired com.bowe.meetstudent.security.JwtProperties jwtProperties;
    @MockitoBean Clock clock;
    Instant start;
    UserEntity user;

    @BeforeEach void setup() {
        start = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        at(start);
        user = users.save(UserEntity.builder().email("inactivity@test.com")
                .password(passwords.encode("password123"))
                .role(roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(com.bowe.meetstudent.entities.Role.builder().name("ROLE_STUDENT").build()))).build());
    }
    void at(Instant instant) { when(clock.instant()).thenReturn(instant); }
    JsonNode login() throws Exception {
        return json.readTree(http.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"inactivity@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode refresh(JsonNode tokens) throws Exception {
        return json.readTree(http.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", tokens.get("refreshToken").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void refused(JsonNode tokens) throws Exception {
        http.perform(get("/api/v1/users/id/" + user.getId())
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", tokens.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }
    @Test void refreshAloneDoesNotExtendSessionAndExactSevenDaysRefusesBothCredentials() throws Exception {
        var tokens = login();
        at(start.plus(Duration.ofDays(7)).minusSeconds(1));
        tokens = refresh(tokens);
        at(start.plus(Duration.ofDays(7)));
        refused(tokens);
        at(start.plus(Duration.ofDays(8)));
        refused(tokens);
    }

    void access(JsonNode tokens, boolean activity, int expected) throws Exception {
        http.perform(get("/api/v1/users/id/" + user.getId())
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                .header("X-Session-Activity", Boolean.toString(activity)))
                .andExpect(status().is(expected));
    }

    @Test void successfulActivityExtendsOnlyItsDeviceAndKeepsAnActiveSessionUsableBeyondSevenDays() throws Exception {
        var active = login();
        var idle = login();
        at(start.plus(Duration.ofDays(6)));
        active = refresh(active);
        idle = refresh(idle);
        http.perform(patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + active.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstname\":\"Active\"}"))
                .andExpect(status().isOk());
        at(start.plus(Duration.ofDays(7)));
        refused(idle);
        active = refresh(active);
        access(active, false, 200);
        at(start.plus(Duration.ofDays(12)));
        active = refresh(active);
        access(active, true, 200);
        at(start.plus(Duration.ofDays(18)));
        active = refresh(active);
        access(active, false, 200);
        at(start.plus(Duration.ofDays(19)));
        refused(active);
    }

    @Test void passiveReadsAndRejectedWritesDoNotExtendAndExpiredActivityCannotRevive() throws Exception {
        var tokens = login();
        at(start.plus(Duration.ofDays(6)));
        tokens = refresh(tokens);
        access(tokens, false, 200);
        http.perform(patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                .header("X-Session-Activity", "true")
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"newPassword123\"}"))
                .andExpect(status().isBadRequest());
        at(start.plus(Duration.ofDays(7)).minusSeconds(1));
        tokens = refresh(tokens);
        at(start.plus(Duration.ofDays(7)));
        access(tokens, true, 401);
        http.perform(patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstname\":\"Too late\"}"))
                .andExpect(status().isUnauthorized());
        refused(tokens);
    }

    @Test void passwordChangeStillRevokesAllIndependentDevices() throws Exception {
        var first = login();
        var second = login();
        http.perform(patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + first.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password123\",\"password\":\"newPassword123\"}"))
                .andExpect(status().isOk());
        refused(first);
        refused(second);
    }

    @Test void historicalCredentialsKeepDeploymentGraceAndRotateWithoutAnAbsoluteRefreshDeadline() throws Exception {
        // The fixture is the V21 migration output (hashed by V35): a bounded compatibility row,
        // retaining the old secret and old fixed expiry, with a stable identity.
        sessions.save(com.bowe.meetstudent.entities.RefreshToken.builder().user(user)
                .token(AuthIntegrationTests.sha256Hex("historical-refresh")).sessionId(java.util.UUID.randomUUID()).legacy(true)
                .lastActivity(start).expiryDate(start.plusSeconds(120)).build());
        var oldAccess = com.auth0.jwt.JWT.create().withSubject(user.getId().toString())
                .withClaim("email", user.getEmail()).withClaim("roles", java.util.List.of("ROLE_STUDENT"))
                .withExpiresAt(start.plusSeconds(120))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(jwtProperties.getSecretKey()));
        http.perform(get("/api/v1/users/id/" + user.getId()).header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isOk());
        var old = json.readTree("{\"refreshToken\":\"historical-refresh\"}");
        at(start.plus(Duration.ofDays(6)));
        var rotated = refresh(old);
        access(rotated, false, 200);
        at(start.plus(Duration.ofDays(7)).minusSeconds(1));
        rotated = refresh(rotated);
        at(start.plus(Duration.ofDays(7)));
        refused(rotated);
    }
}
