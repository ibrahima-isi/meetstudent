package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.models.LoginResponse;
import com.bowe.meetstudent.security.JwtIssuer;
import com.bowe.meetstudent.security.UserPrincipal;
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
class AuthControllerUnitTest {

    @Mock
    private JwtIssuer jwtIssuer;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_shouldReturnAccessToken_whenCredentialsAreValid() {
        // Arrange
        String email = "test@example.com";
        String password = "password";
        String expectedToken = "valid_jwt_token";

        LoginRequest request = LoginRequest.builder()
                .username(email)
                .password(password)
                .build();

        // Mock Authentication
        Authentication authentication = mock(Authentication.class);
        UserPrincipal principal = UserPrincipal.builder()
                .id(1)
                .username(email)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);

        // Mock JWT Issue
        when(jwtIssuer.issueToken(principal.getId(), principal.getUsername(), List.of("ROLE_USER")))
                .thenReturn(expectedToken);

        // Act
        LoginResponse response = authController.login(request);

        // Assert
        assertEquals(expectedToken, response.getAccessToken());
    }
}
