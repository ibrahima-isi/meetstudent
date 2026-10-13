package com.bowe.meetstudent.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtIssuer {
    private final JwtProperties properties;

    /** {@code passwordVersion} goes into the "pv" claim; a later password change makes the token invalid. */
    public String issueToken(long userId, String email, List<String> roles, int passwordVersion){
        var now = Instant.now();
        return JWT.create()
                .withSubject(String.valueOf(userId))
                // informational; revocation is decided by the "pv" claim, never by clock comparisons
                .withIssuedAt(now)
                .withExpiresAt(now.plus(Duration.ofMinutes(properties.getAccessTtlMinutes())))
                .withClaim("email", email)
                .withClaim("roles", roles)
                .withClaim("pv", passwordVersion)
                .sign(Algorithm.HMAC256(properties.getSecretKey()));
    }
}
