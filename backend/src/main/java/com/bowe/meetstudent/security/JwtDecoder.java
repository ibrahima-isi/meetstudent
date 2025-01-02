package com.bowe.meetstudent.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decode and verify the token
 */
@Component
@RequiredArgsConstructor
public class JwtDecoder {

    private final JwtProperties properties;
    public DecodedJWT decodeToken(String token){
        return JWT.require(Algorithm.HMAC256(properties.getSecretKey())) // our secret from application
                .build()
                .verify(token); // secret key passed by the user
    }
}
