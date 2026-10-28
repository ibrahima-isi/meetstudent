package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.RefreshToken;
import com.bowe.meetstudent.entities.RefreshTokenHistory;
import com.bowe.meetstudent.exceptions.InvalidRefreshTokenException;
import com.bowe.meetstudent.repositories.RefreshTokenHistoryRepository;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private java.time.Clock clock;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @org.junit.jupiter.api.BeforeEach
    void time() { lenient().when(clock.instant()).thenAnswer(invocation -> Instant.now()); }

    @InjectMocks
    private RefreshTokenService service;

    @Test
    void verifyExpiration_throwsInvalidRefreshTokenAndDeletesToken_whenExpired() {
        RefreshToken token = RefreshToken.builder().token("t").lastActivity(Instant.now().minus(Duration.ofDays(8))).build();

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
        when(userRepository.findByIdForUpdate(1)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var issued = service.createRefreshToken(1, 0);

        assertEquals(sha256Hex(issued.token()), issued.session().getToken());
        org.junit.jupiter.api.Assertions.assertNotEquals(issued.token(), issued.session().getToken());
    }

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final UUID SID = UUID.fromString("6a3c1f0e-2b4d-4e8a-9c7f-1d2e3f4a5b6c");

    private static RefreshToken session(String currentSecret) throws Exception {
        var user = com.bowe.meetstudent.entities.UserEntity.builder().id(1).passwordVersion(0).build();
        return RefreshToken.builder().id(10).user(user).sessionId(SID).passwordVersion(0).lastActivity(NOW)
                .expiryDate(NOW.plus(Duration.ofDays(7))).token(sha256Hex(currentSecret)).build();
    }

    private static RefreshTokenHistory rotatedAway(int id, RefreshToken session, String secret, Instant at) throws Exception {
        return RefreshTokenHistory.builder().id(id).refreshToken(session).tokenHash(sha256Hex(secret)).rotatedAt(at).build();
    }

    /** {@code entry} (a rotated-away secret of a session whose latest history id is {@code latestId}) is replayed. */
    private void replayed(RefreshTokenHistory entry, int latestId) {
        var hash = entry.getTokenHash();
        when(refreshTokenRepository.findUserIdByToken(hash)).thenReturn(Optional.empty());
        when(historyRepository.findUserIdByTokenHash(hash)).thenReturn(Optional.of(1));
        when(userRepository.findByIdForUpdate(1)).thenReturn(Optional.of(entry.getRefreshToken().getUser()));
        when(refreshTokenRepository.findByToken(hash)).thenReturn(Optional.empty());
        when(historyRepository.findByTokenHash(hash)).thenReturn(Optional.of(entry));
        lenient().when(historyRepository.findLatestId(10)).thenReturn(Optional.of(latestId));
    }

    @Test
    void rotate_withTheCurrentSecret_recordsItInTheHistoryPurgesOldEntriesAndReplacesIt() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        var session = session("s1");
        when(refreshTokenRepository.findUserIdByToken(sha256Hex("s1"))).thenReturn(Optional.of(1));
        when(userRepository.findByIdForUpdate(1)).thenReturn(Optional.of(session.getUser()));
        when(refreshTokenRepository.findByToken(sha256Hex("s1"))).thenReturn(Optional.of(session));
        when(refreshTokenRepository.save(session)).thenReturn(session);

        var rotation = service.rotate("s1");

        assertEquals(sha256Hex(rotation.token()), session.getToken());
        var recorded = ArgumentCaptor.forClass(RefreshTokenHistory.class);
        verify(historyRepository).save(recorded.capture());
        assertSame(session, recorded.getValue().getRefreshToken());
        assertEquals(sha256Hex("s1"), recorded.getValue().getTokenHash());
        assertEquals(NOW, recorded.getValue().getRotatedAt());
        // Lock ordering: the user row first, then the tokens; the purge stays inside this user's sessions.
        var order = inOrder(userRepository, historyRepository);
        order.verify(userRepository).findByIdForUpdate(1);
        order.verify(historyRepository).purgeRotatedBefore(1, NOW.minus(Duration.ofDays(7)));
        verify(refreshTokenRepository, never()).deleteBySessionIdAndUserId(any(), any());
    }

    @Test
    void rotate_withAnOlderRotatedAwaySecret_endsTheSessionEvenInsideTheGraceWindow() throws Exception {
        lenient().when(clock.instant()).thenReturn(NOW);
        var session = session("s3");
        var first = rotatedAway(1, session, "s1", NOW.minusSeconds(2));
        replayed(first, 2);

        var refused = assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("s1"));

        assertEquals("Refresh token is not in database!", refused.getMessage());
        assertInstanceOf(RefreshTokenService.RefreshTokenReuseException.class, refused);
        verify(refreshTokenRepository).deleteBySessionIdAndUserId(SID, 1);
        verify(refreshTokenRepository, never()).deleteAllByUserId(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_withTheLatestRotatedAwaySecretOnceTheGraceWindowIsOver_endsTheSessionLikeAnUnknownToken() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        var session = session("s2");
        var latest = rotatedAway(2, session, "s1", NOW.minusSeconds(10));
        replayed(latest, 2);

        var refused = assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("s1"));

        assertEquals("Refresh token is not in database!", refused.getMessage());
        // The revocation must survive the request's transaction, which keeps its writes for this type only.
        assertInstanceOf(RefreshTokenService.RefreshTokenReuseException.class, refused);
        verify(refreshTokenRepository).deleteBySessionIdAndUserId(SID, 1);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_withTheLatestRotatedAwaySecretInsideTheGraceWindow_refusesWithoutEndingTheSession() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        var session = session("s2");
        var latest = rotatedAway(2, session, "s1", NOW.minusMillis(9_999));
        replayed(latest, 2);

        var refused = assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("s1"));

        assertEquals("Refresh token is not in database!", refused.getMessage());
        assertFalse(refused instanceof RefreshTokenService.RefreshTokenReuseException);
        verify(refreshTokenRepository, never()).deleteBySessionIdAndUserId(any(), any());
        verify(refreshTokenRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        assertEquals(sha256Hex("s2"), session.getToken());
    }

    @Test
    void rotate_withAnUnknownSecret_refusesWithoutTouchingAnySession() throws Exception {
        when(refreshTokenRepository.findUserIdByToken(sha256Hex("nope"))).thenReturn(Optional.empty());
        when(historyRepository.findUserIdByTokenHash(sha256Hex("nope"))).thenReturn(Optional.empty());

        var refused = assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("nope"));

        assertEquals("Refresh token is not in database!", refused.getMessage());
        assertFalse(refused instanceof RefreshTokenService.RefreshTokenReuseException);
        verify(userRepository, never()).findByIdForUpdate(any());
        verify(refreshTokenRepository, never()).deleteBySessionIdAndUserId(any(), any());
        verify(refreshTokenRepository, never()).deleteAllByUserId(any());
    }

    private static String sha256Hex(String raw) throws java.security.NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
