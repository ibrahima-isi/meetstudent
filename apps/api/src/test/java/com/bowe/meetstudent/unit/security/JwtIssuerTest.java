package com.bowe.meetstudent.unit.security;

import com.auth0.jwt.JWT;
import com.bowe.meetstudent.security.JwtIssuer;
import com.bowe.meetstudent.security.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtIssuerTest {

    private static final long TOLERANCE_SECONDS = 5;

    private JwtIssuer issuerWith(JwtProperties properties) {
        properties.setSecretKey("unit-test-secret-key-0123456789");
        return new JwtIssuer(properties);
    }

    private long secondsUntilExpiry(String token) {
        return Duration.between(Instant.now(), JWT.decode(token).getExpiresAtAsInstant()).toSeconds();
    }

    @Test
    void issueToken_defaultsToSixtyMinutes() {
        var issuer = issuerWith(new JwtProperties());

        long seconds = secondsUntilExpiry(issuer.issueToken(1, "a@b.c", List.of("ROLE_STUDENT")));

        assertTrue(Math.abs(seconds - 60 * 60) <= TOLERANCE_SECONDS, "expected ~60 min but was " + seconds + "s");
    }

    @Test
    void issueToken_usesConfiguredLifetime() {
        var properties = new JwtProperties();
        properties.setAccessTtlMinutes(5);
        var issuer = issuerWith(properties);

        long seconds = secondsUntilExpiry(issuer.issueToken(1, "a@b.c", List.of("ROLE_STUDENT")));

        assertTrue(Math.abs(seconds - 5 * 60) <= TOLERANCE_SECONDS, "expected ~5 min but was " + seconds + "s");
    }

    @Test
    void defaultAccessTtlIsSixtyMinutes() {
        assertEquals(60, new JwtProperties().getAccessTtlMinutes());
    }
}
