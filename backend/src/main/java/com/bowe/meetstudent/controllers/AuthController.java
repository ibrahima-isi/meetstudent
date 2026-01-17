package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.models.LoginResponse;
import com.bowe.meetstudent.security.JwtIssuer;
import com.bowe.meetstudent.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "1. Authentication", description = "Endpoints for user authentication")
public class AuthController {
    private final JwtIssuer jwtIssuer;
    private final AuthenticationManager authenticationManager;

    @PostMapping
    @Operation(summary = "Authenticate user and get JWT", description = "Authenticates a user with username (email) and password, then returns a JWT access token.")
    @ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public LoginResponse login(@RequestBody @Validated LoginRequest request){
        // Find the correct user and generate token from his infos:
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var principal = (UserPrincipal) authentication.getPrincipal();
        var roles = principal.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        var token = jwtIssuer.issueToken(principal.getId(), principal.getUsername(), roles);

        return  LoginResponse.builder()
                .accessToken(token)
                .build();
    }
}
