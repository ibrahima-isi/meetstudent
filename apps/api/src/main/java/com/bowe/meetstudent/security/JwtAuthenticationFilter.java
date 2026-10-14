package com.bowe.meetstudent.security;

import com.bowe.meetstudent.services.RefreshTokenService;
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
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LogManager.getLogger(JwtAuthenticationFilter.class);
    private final JwtDecoder jwtDecoder;
    private final JwtToPrincipalConverter jwtToPrincipalConverter;
    private final RefreshTokenService refreshTokenService;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        UUID sessionId = null;
        Integer userId = null;
        int version = 0;
        try {
            var token = extractTokenFromRequest(request);
            if (token.isPresent()) {
                var jwt = jwtDecoder.decodedJWT(token.get());
                userId = Integer.valueOf(jwt.getSubject());
                var claim = jwt.getClaim("pv");
                version = claim.isNull() || claim.isMissing() ? 0 : claim.asInt();
                var accepted = refreshTokenService.authorize(userId, version, jwt.getClaim("sid").asString());
                if (accepted.isPresent()) {
                    sessionId = accepted.get();
                    SecurityContextHolder.getContext().setAuthentication(
                            new UserPrincipalAuthenticationToken(jwtToPrincipalConverter.convert(jwt)));
                }
            }
        } catch (Exception e) {
            logger.warn("JWT authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
        if (sessionId != null && response.getStatus() >= 200 && response.getStatus() < 300
                && request.getRequestURI().startsWith("/api/v1/")
                && !request.getRequestURI().startsWith("/api/v1/auth")
                && (Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())
                    || (request.getMethod().equals("GET") && "true".equals(request.getHeader("X-Session-Activity"))))) {
            refreshTokenService.recordActivity(userId, sessionId, version);
        }
    }

    private Optional<String> extractTokenFromRequest(HttpServletRequest request){
        var token = request.getHeader("Authorization");
        if(StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            return Optional.of(token.substring(7));
        }
        return Optional.empty();
    }
}
