package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.RefreshToken;
import com.bowe.meetstudent.entities.RefreshTokenHistory;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.exceptions.InvalidRefreshTokenException;
import com.bowe.meetstudent.repositories.RefreshTokenHistoryRepository;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Independent device sessions: the refresh secret rotates, the identity and activity do not.
 * Everything that creates, rotates, extends or deletes them first takes the
 * user's row lock ({@link UserRepository#findByIdForUpdate}), the same lock credential changes take, so a refresh
 * and a password change never interleave.
 * Only the SHA-256 of a refresh secret is stored (same hashing as {@link UserTokenService}); the clear value leaves
 * the service once, in {@link IssuedSession} or {@link Rotation}, for the response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofDays(7);
    /** How long after a rotation a replay of the latest rotated-away secret is refused without ending the session. */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);
    private static final String UNKNOWN_TOKEN = "Refresh token is not in database!";

    /** A new session and its refresh secret in clear, never stored. */
    public record IssuedSession(RefreshToken session, String token) {}

    /** The outcome of a rotation: the (locked, current) user, the session and its replacement secret in clear. */
    public record Rotation(UserEntity user, RefreshToken refreshToken, String token) {}

    /**
     * A rotated-away secret was presented again and its session has been ended. Answered exactly like an unknown
     * token; the transactions around {@link #rotate} must not roll back for it, or the revocation is lost.
     */
    public static class RefreshTokenReuseException extends InvalidRefreshTokenException {
        public RefreshTokenReuseException() {
            super(UNKNOWN_TOKEN);
        }
    }

    private final Clock clock;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(UserTokenService.hash(token));
    }

    /**
     * Creates the user's refresh token after a successful authentication. {@code authenticatedVersion} is the
     * password version the credentials were checked against: if the password changed since, the login lost the
     * race against the change and is refused.
     */
    @Transactional
    public IssuedSession createRefreshToken(Integer userId, int authenticatedVersion) {
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("User no longer exists"));
        // Authentication loaded this entity before taking the lock: discard that stale snapshot.
        entityManager.refresh(user);
        if (user.getPasswordVersion() != authenticatedVersion) {
            throw new InvalidRefreshTokenException("Credentials changed during sign-in");
        }
        var now = clock.instant();
        var secret = UUID.randomUUID().toString();
        var session = refreshTokenRepository.save(RefreshToken.builder().user(user)
                .sessionId(UUID.randomUUID()).lastActivity(now)
                .expiryDate(now.plus(INACTIVITY_TIMEOUT))
                .token(UserTokenService.hash(secret)).passwordVersion(user.getPasswordVersion()).build());
        return new IssuedSession(session, secret);
    }

    /**
     * Validates a refresh token and replaces it, under the owner's row lock. Unknown, expired, deleted by a
     * concurrent credential change, or created under an older password version: all the same 401.
     * <p>
     * Reuse detection: every secret a session rotates away stays in {@link RefreshTokenHistory} for the inactivity
     * window. Presenting one again means two parties hold copies of the token (the legitimate client and a thief, in
     * either order, however many times the other one refreshed since), so the whole session is ended (its access
     * tokens and history die with it) and the answer is the same 401 as an unknown token, through
     * {@link RefreshTokenReuseException} so the revocation commits. Only the latest rotated-away secret, within
     * {@link #REUSE_GRACE} of its rotation, is merely refused: two tabs or a retry racing the same refresh is not a
     * theft. History older than the inactivity window is purged on rotation; such a secret is then unknown.
     */
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public Rotation rotate(String tokenValue) {
        var hash = UserTokenService.hash(tokenValue);
        Integer userId = refreshTokenRepository.findUserIdByToken(hash)
                .or(() -> historyRepository.findUserIdByTokenHash(hash))
                .orElseThrow(RefreshTokenService::unknownToken);
        UserEntity user = userRepository.findByIdForUpdate(userId).orElseThrow(RefreshTokenService::unknownToken);
        entityManager.refresh(user);
        // Re-read under the lock: a change that committed while we waited has deleted or rotated the row.
        var current = refreshTokenRepository.findByToken(hash);
        if (current.isEmpty()) {
            throw refuseReplayedSecret(hash, userId);
        }
        RefreshToken token = current.get();
        verifyExpiration(token);
        if (token.getPasswordVersion() != user.getPasswordVersion()) {
            refreshTokenRepository.deleteAllByUserId(userId);
            throw new InvalidRefreshTokenException("Refresh token predates a credential change");
        }
        var now = clock.instant();
        historyRepository.purgeRotatedBefore(userId, now.minus(INACTIVITY_TIMEOUT));
        historyRepository.save(RefreshTokenHistory.builder().refreshToken(token).tokenHash(hash).rotatedAt(now).build());
        var secret = UUID.randomUUID().toString();
        token.setToken(UserTokenService.hash(secret));
        return new Rotation(user, refreshTokenRepository.save(token), secret);
    }

    /**
     * The refusal for a hash that is not a current secret (called under the owner's lock). When a session rotated
     * it away, that session is ended first, unless it is its latest rotation and still within the grace window.
     */
    private InvalidRefreshTokenException refuseReplayedSecret(String hash, Integer userId) {
        var replayed = historyRepository.findByTokenHash(hash);
        if (replayed.isEmpty()) {
            return unknownToken();
        }
        var entry = replayed.get();
        var session = entry.getRefreshToken();
        boolean latest = historyRepository.findLatestId(session.getId()).filter(entry.getId()::equals).isPresent();
        if (latest && clock.instant().isBefore(entry.getRotatedAt().plus(REUSE_GRACE))) {
            return unknownToken();
        }
        refreshTokenRepository.deleteBySessionIdAndUserId(session.getSessionId(), userId);
        log.warn("Refresh token reuse detected: session {} of user {} ended", session.getSessionId(), userId);
        return new RefreshTokenReuseException();
    }

    private static InvalidRefreshTokenException unknownToken() {
        return new InvalidRefreshTokenException(UNKNOWN_TOKEN);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (!isActiveAt(token, clock.instant())) {
            refreshTokenRepository.delete(token);
            throw new InvalidRefreshTokenException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    /** Authorization reads current persisted state; already-authorized operations are not rolled back by revocation. */
    @Transactional
    public Optional<UUID> authorize(Integer userId, int passwordVersion, String sessionId) {
        var version = userRepository.findPasswordVersionById(userId);
        if (version.isEmpty() || version.get() != passwordVersion) return Optional.empty();
        Optional<RefreshToken> session = sessionId == null
                ? refreshTokenRepository.findLegacySessions(userId).stream().findFirst()
                : refreshTokenRepository.findBySessionIdAndUserId(UUID.fromString(sessionId), userId);
        if (session.isEmpty()) return Optional.empty();
        var token = session.get();
        entityManager.refresh(token);
        if (token.getPasswordVersion() != passwordVersion) return Optional.empty();
        verifyExpiration(token);
        return Optional.of(token.getSessionId());
    }

    /** A successful business request can extend only a still-live session; never revive an expired one. */
    @Transactional
    public void recordActivity(Integer userId, UUID sessionId, int passwordVersion) {
        var user = userRepository.findByIdForUpdate(userId);
        if (user.isEmpty()) return;
        entityManager.refresh(user.get());
        if (user.get().getPasswordVersion() != passwordVersion) return;
        refreshTokenRepository.findBySessionIdAndUserId(sessionId, userId).ifPresent(token -> {
            entityManager.refresh(token);
            var now = clock.instant();
            if (token.getPasswordVersion() == passwordVersion
                    && isActiveAt(token, now)
                    && now.isAfter(token.getLastActivity())) {
                token.setLastActivity(now);
                token.setExpiryDate(now.plus(INACTIVITY_TIMEOUT));
            }
        });
    }

    private boolean isActiveAt(RefreshToken session, Instant now) {
        return session.getLastActivity().plus(INACTIVITY_TIMEOUT).isAfter(now);
    }

    /**
     * Sign-out: ends the session the refresh token belongs to, under the owner's row lock. The session is resolved
     * before the lock and deleted by its stable {@code sid}, so a rotation that commits while we wait cannot save it.
     * Unknown or already ended: nothing to do.
     */
    @Transactional
    public void logout(String tokenValue) {
        refreshTokenRepository.findByToken(UserTokenService.hash(tokenValue)).ifPresent(token -> {
            Integer userId = token.getUser().getId();
            if (userRepository.findByIdForUpdate(userId).isPresent()) {
                refreshTokenRepository.deleteBySessionIdAndUserId(token.getSessionId(), userId);
            }
        });
    }

    /** Ends every session of the user. Callers hold the user's row lock. */
    @Transactional
    public int deleteByUserId(Integer userId) {
        return refreshTokenRepository.deleteAllByUserId(userId);
    }
}
