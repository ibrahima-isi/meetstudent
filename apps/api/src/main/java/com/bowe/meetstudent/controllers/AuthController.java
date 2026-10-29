package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.models.LoginResponse;
import com.bowe.meetstudent.models.TokenRefreshRequest;
import com.bowe.meetstudent.security.JwtIssuer;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "01. Authentication", description = "Endpoints for user authentication")
public class AuthController {
    private final JwtIssuer jwtIssuer;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @PostMapping
    @Operation(summary = "Authenticate user and get JWT", description = "Authenticates a user with username (email) and password, then returns a JWT access token and a refresh token.")
    @ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @org.springframework.transaction.annotation.Transactional
    public LoginResponse login(@RequestBody @Validated LoginRequest request){
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var principal = (UserPrincipal) authentication.getPrincipal();
        var roles = principal.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        int version = principal.getPasswordVersion() == null ? 0 : principal.getPasswordVersion();
        var issued = refreshTokenService.createRefreshToken(principal.getId(), version);
        var token = jwtIssuer.issueToken(principal.getId(), principal.getUsername(), roles, version, issued.session().getSessionId());

        return LoginResponse.builder()
                .accessToken(token)
                .refreshToken(issued.token())
                .build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Uses a valid refresh token to issue a new access token and a new refresh token (rotation). Presenting a refresh token that was already rotated away (more than a few seconds earlier) ends that session; the answer is the same 401 as for an unknown token.")
    // A detected reuse has just deleted the session: commit that, even though the request fails with 401.
    @org.springframework.transaction.annotation.Transactional(noRollbackFor = RefreshTokenService.RefreshTokenReuseException.class)
    public LoginResponse refreshToken(@RequestBody @Validated TokenRefreshRequest request) {
        var rotation = refreshTokenService.rotate(request.getRefreshToken());
        var user = rotation.user();
        String roleName = user.getRole().getName();
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }
        var newAccessToken = jwtIssuer.issueToken(user.getId(), user.getEmail(), List.of(roleName), user.getPasswordVersion(), rotation.refreshToken().getSessionId());
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(rotation.token())
                .build();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out", description = "Ends the session (device) the refresh token belongs to; its access and refresh tokens stop working. Other sessions are kept. Idempotent: an unknown or already ended session also answers 204.")
    @ApiResponse(responseCode = "204", description = "Session ended, or already gone")
    @ApiResponse(responseCode = "400", description = "Missing refresh token")
    public void logout(@RequestBody @Validated TokenRefreshRequest request) {
        refreshTokenService.logout(request.getRefreshToken());
    }
}
