package com.bowe.meetstudent.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Issue a JWT token for the SecurityFilterChain in the security config
 */
@Component
@RequiredArgsConstructor
public class JwtIssuer {

    private final JwtProperties propertiesValue;

    public String issue(Long userId, String email, List<String> roles){
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withExpiresAt(Instant.now().plus(Duration.of(1, ChronoUnit.DAYS)))
                .withClaim("email", email)
                .withClaim("role", roles)
                .sign(
                        Algorithm.HMAC256(
                                propertiesValue.getSecretKey()
                        )
                );
    }
}
