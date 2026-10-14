package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.RefreshToken;
import com.bowe.meetstudent.exceptions.InvalidRefreshTokenException;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private java.time.Clock clock;

    @org.junit.jupiter.api.BeforeEach
    void time() { org.mockito.Mockito.when(clock.instant()).thenAnswer(invocation -> Instant.now()); }

    @InjectMocks
    private RefreshTokenService service;

    @Test
    void verifyExpiration_throwsInvalidRefreshTokenAndDeletesToken_whenExpired() {
        RefreshToken token = RefreshToken.builder().token("t").lastActivity(Instant.now().minus(java.time.Duration.ofDays(8))).build();

        assertThrows(InvalidRefreshTokenException.class, () -> service.verifyExpiration(token));
        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void verifyExpiration_returnsToken_whenStillValid() {
        RefreshToken token = RefreshToken.builder().token("t").lastActivity(Instant.now()).build();

        assertSame(token, service.verifyExpiration(token));
        verify(refreshTokenRepository, never()).delete(token);
    }
}
