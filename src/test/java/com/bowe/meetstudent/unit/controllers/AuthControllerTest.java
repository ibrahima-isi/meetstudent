package com.bowe.meetstudent.unit.controllers;

import com.bowe.meetstudent.controllers.AuthController;
import com.bowe.meetstudent.entities.RefreshToken;
import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.models.LoginResponse;
import com.bowe.meetstudent.security.JwtIssuer;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private JwtIssuer jwtIssuer;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_shouldReturnAccessToken_whenCredentialsAreValid() {
        // Arrange
        String email = "test@example.com";
        String password = "password";
        String expectedToken = "valid_jwt_token";
        String expectedRefreshToken = "valid_refresh_token";

        LoginRequest request = LoginRequest.builder()
                .username(email)
                .password(password)
                .build();

        // Mock Authentication
        Authentication authentication = mock(Authentication.class);
        UserPrincipal principal = UserPrincipal.builder()
                .id(1)
                .username(email)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);

        // Mock JWT Issue
        when(jwtIssuer.issueToken(principal.getId(), principal.getUsername(), List.of("ROLE_STUDENT")))
                .thenReturn(expectedToken);
        
        // Mock Refresh Token
        RefreshToken refreshToken = RefreshToken.builder().token(expectedRefreshToken).build();
        when(refreshTokenService.createRefreshToken(principal.getId())).thenReturn(refreshToken);

        // Act
        LoginResponse response = authController.login(request);

        // Assert
        assertEquals(expectedToken, response.getAccessToken());
        assertEquals(expectedRefreshToken, response.getRefreshToken());
    }
}
