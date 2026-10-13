package com.bowe.meetstudent.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.bowe.meetstudent.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LogManager.getLogger(JwtAuthenticationFilter.class);
    private final JwtDecoder jwtDecoder;
    private final JwtToPrincipalConverter jwtToPrincipalConverter;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        try{
            extractTokenFromRequest(request)
                .map(jwtDecoder::decodedJWT)
                .filter(this::matchesPasswordVersion)
                .map(jwtToPrincipalConverter::convert)
                .map(UserPrincipalAuthenticationToken::new)
                .ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        }catch (Exception e){
            logger.warn("JWT authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }


        filterChain.doFilter(request, response);
    }

    /**
     * An access token is accepted only while its "pv" claim equals the user's current {@code passwordVersion};
     * a token without the claim counts as version 0 (minted before the claim existed), so it works until the
     * account's first password change. A token for a user that no longer exists is refused. One primary-key
     * query per authenticated request.
     */
    private boolean matchesPasswordVersion(DecodedJWT jwt) {
        var userId = Integer.valueOf(jwt.getSubject());
        var current = userRepository.findPasswordVersionById(userId);
        if (current.isEmpty()) {
            logger.warn("JWT refused: user no longer exists, userId={}", userId);
            return false;
        }
        var claim = jwt.getClaim("pv");
        int tokenVersion = claim.isNull() || claim.isMissing() ? 0 : claim.asInt();
        if (tokenVersion != current.get()) {
            logger.warn("JWT refused: issued before the last password change, userId={}", userId);
            return false;
        }
        return true;
    }

    private Optional<String> extractTokenFromRequest(HttpServletRequest request){
        var token = request.getHeader("Authorization");
        if(StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            return Optional.of(token.substring(7));
        }
        return Optional.empty();
    }
}
