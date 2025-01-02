package com.bowe.meetstudent.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convert the token to a Principal by decoding the token and getting the details.
 */
@Component
public class JwtToPrincipalConverter {
    public UserPrincipal convertToPrincipal(DecodedJWT jwt){
        var authorities = extractAuthoritiesFromClaim(jwt);

        return UserPrincipal.builder()
                .email(jwt.getClaim("email").asString())
                .grantedAuthorities(authorities)
                .build();
    }

    private List<SimpleGrantedAuthority> extractAuthoritiesFromClaim(DecodedJWT jwt){
        var claim = jwt.getClaim("role");
        if(claim.isNull() || claim.isMissing()) return List.of();

        return claim.asList(SimpleGrantedAuthority.class);
    }
}
