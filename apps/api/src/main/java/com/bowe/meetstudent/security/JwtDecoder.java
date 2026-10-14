package com.bowe.meetstudent.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import java.time.Clock;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtDecoder {
    private final JwtProperties properties;
    private final Clock clock;

    public DecodedJWT decodedJWT(String token){
        return ((JWTVerifier.BaseVerification) JWT.require(Algorithm.HMAC256(properties.getSecretKey())))
                .build(clock).verify(token);

    }
}
