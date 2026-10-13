package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.RefreshToken;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.exceptions.InvalidRefreshTokenException;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens: one per user, rotated on use. Everything that creates, rotates or deletes them first takes the
 * user's row lock ({@link UserRepository#findByIdForUpdate}), the same lock credential changes take, so a refresh
 * and a password change never interleave.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** The outcome of a rotation: the (locked, current) user and the replacement refresh token. */
    public record Rotation(UserEntity user, RefreshToken refreshToken) {}

    @Value("${security.jwt.refresh-token-expiration:604800000}") // 7 days default
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Creates the user's refresh token after a successful authentication. {@code authenticatedVersion} is the
     * password version the credentials were checked against: if the password changed since, the login lost the
     * race against the change and is refused.
     */
    @Transactional
    public RefreshToken createRefreshToken(Integer userId, int authenticatedVersion) {
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("User no longer exists"));
        // Authentication loaded this entity before taking the lock: discard that stale snapshot.
        entityManager.refresh(user);
        if (user.getPasswordVersion() != authenticatedVersion) {
            throw new InvalidRefreshTokenException("Credentials changed during sign-in");
        }
        return replaceToken(user);
    }

    /**
     * Validates a refresh token and replaces it, under the owner's row lock. Unknown, expired, deleted by a
     * concurrent credential change, or created under an older password version: all the same 401.
     */
    @Transactional
    public Rotation rotate(String tokenValue) {
        Integer userId = refreshTokenRepository.findUserIdByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is not in database!"));
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is not in database!"));
        // Re-read under the lock: a change that committed while we waited has deleted the row.
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is not in database!"));
        verifyExpiration(token);
        if (token.getPasswordVersion() != user.getPasswordVersion()) {
            refreshTokenRepository.deleteAllByUserId(userId);
            throw new InvalidRefreshTokenException("Refresh token predates a credential change");
        }
        return new Rotation(user, replaceToken(user));
    }

    private RefreshToken replaceToken(UserEntity user) {
        refreshTokenRepository.deleteAllByUserId(user.getId());
        refreshTokenRepository.flush();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .token(UUID.randomUUID().toString())
                .passwordVersion(user.getPasswordVersion())
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new InvalidRefreshTokenException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    /** Ends every session of the user. Callers hold the user's row lock. */
    @Transactional
    public int deleteByUserId(Integer userId) {
        return refreshTokenRepository.deleteAllByUserId(userId);
    }
}
