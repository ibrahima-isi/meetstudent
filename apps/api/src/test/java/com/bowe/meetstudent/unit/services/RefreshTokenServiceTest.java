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

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @org.junit.jupiter.api.BeforeEach
    void time() { org.mockito.Mockito.lenient().when(clock.instant()).thenAnswer(invocation -> Instant.now()); }

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

    @Test
    void createRefreshToken_storesTheSha256AndReturnsTheClearTokenOnce() throws Exception {
        var user = com.bowe.meetstudent.entities.UserEntity.builder().id(1).passwordVersion(0).build();
        org.mockito.Mockito.when(userRepository.findByIdForUpdate(1)).thenReturn(java.util.Optional.of(user));
        org.mockito.Mockito.when(refreshTokenRepository.save(org.mockito.ArgumentMatchers.any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var issued = service.createRefreshToken(1, 0);

        org.junit.jupiter.api.Assertions.assertEquals(sha256Hex(issued.token()), issued.session().getToken());
        org.junit.jupiter.api.Assertions.assertNotEquals(issued.token(), issued.session().getToken());
    }

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final java.util.UUID SID = java.util.UUID.fromString("6a3c1f0e-2b4d-4e8a-9c7f-1d2e3f4a5b6c");

    private RefreshToken session(String currentSecret, String previousSecret, Instant rotatedAt) throws Exception {
        var user = com.bowe.meetstudent.entities.UserEntity.builder().id(1).passwordVersion(0).build();
        return RefreshToken.builder().id(10).user(user).sessionId(SID).passwordVersion(0).lastActivity(NOW)
                .expiryDate(NOW.plus(java.time.Duration.ofDays(7))).token(sha256Hex(currentSecret))
                .previousToken(previousSecret == null ? null : sha256Hex(previousSecret)).rotatedAt(rotatedAt).build();
    }

    /** A replay of {@code secret}, the previous secret of {@code session}, reaches the locked re-read. */
    private void replayedPrevious(String secret, RefreshToken session) throws Exception {
        org.mockito.Mockito.when(refreshTokenRepository.findUserIdByToken(sha256Hex(secret))).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(refreshTokenRepository.findUserIdByPreviousToken(sha256Hex(secret))).thenReturn(java.util.Optional.of(1));
        org.mockito.Mockito.when(userRepository.findByIdForUpdate(1)).thenReturn(java.util.Optional.of(session.getUser()));
        org.mockito.Mockito.when(refreshTokenRepository.findByToken(sha256Hex(secret))).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(refreshTokenRepository.findByPreviousToken(sha256Hex(secret))).thenReturn(java.util.Optional.of(session));
    }

    @Test
    void rotate_withTheCurrentSecret_replacesItAndRemembersItsHashAsThePreviousOne() throws Exception {
        org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
        var session = session("s1", null, null);
        org.mockito.Mockito.when(refreshTokenRepository.findUserIdByToken(sha256Hex("s1"))).thenReturn(java.util.Optional.of(1));
        org.mockito.Mockito.when(userRepository.findByIdForUpdate(1)).thenReturn(java.util.Optional.of(session.getUser()));
        org.mockito.Mockito.when(refreshTokenRepository.findByToken(sha256Hex("s1"))).thenReturn(java.util.Optional.of(session));
        org.mockito.Mockito.when(refreshTokenRepository.save(session)).thenReturn(session);

        var rotation = service.rotate("s1");

        org.junit.jupiter.api.Assertions.assertEquals(sha256Hex(rotation.token()), session.getToken());
        org.junit.jupiter.api.Assertions.assertEquals(sha256Hex("s1"), session.getPreviousToken());
        org.junit.jupiter.api.Assertions.assertEquals(NOW, session.getRotatedAt());
        verify(refreshTokenRepository, never()).deleteBySessionIdAndUserId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rotate_withThePreviousSecretOnceTheGraceWindowIsOver_endsThatSessionAndAnswersLikeAnUnknownToken() throws Exception {
        org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
        var session = session("s2", "s1", NOW.minusSeconds(10));
        replayedPrevious("s1", session);

        var refused = assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("s1"));

        org.junit.jupiter.api.Assertions.assertEquals("Refresh token is not in database!", refused.getMessage());
        // The revocation must survive the request's transaction, which keeps its writes for this type only.
        org.junit.jupiter.api.Assertions.assertInstanceOf(RefreshTokenService.RefreshTokenReuseException.class, refused);
        verify(refreshTokenRepository).deleteBySessionIdAndUserId(SID, 1);
        verify(refreshTokenRepository, never()).deleteAllByUserId(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rotate_withThePreviousSecretInsideTheGraceWindow_refusesWithoutEndingTheSession() throws Exception {
        org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
        var session = session("s2", "s1", NOW.minusMillis(9_999));
        replayedPrevious("s1", session);

        var refused = assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("s1"));

        org.junit.jupiter.api.Assertions.assertEquals("Refresh token is not in database!", refused.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(refused instanceof RefreshTokenService.RefreshTokenReuseException);
        verify(refreshTokenRepository, never()).deleteBySessionIdAndUserId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertEquals(sha256Hex("s2"), session.getToken());
    }

    @Test
    void rotate_withAnUnknownSecret_refusesWithoutTouchingAnySession() throws Exception {
        org.mockito.Mockito.when(refreshTokenRepository.findUserIdByToken(sha256Hex("nope"))).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(refreshTokenRepository.findUserIdByPreviousToken(sha256Hex("nope"))).thenReturn(java.util.Optional.empty());

        var refused = assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("nope"));

        org.junit.jupiter.api.Assertions.assertEquals("Refresh token is not in database!", refused.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(refused instanceof RefreshTokenService.RefreshTokenReuseException);
        verify(userRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).deleteBySessionIdAndUserId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).deleteAllByUserId(org.mockito.ArgumentMatchers.any());
    }

    private static String sha256Hex(String raw) throws java.security.NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
