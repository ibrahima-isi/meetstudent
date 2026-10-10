package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.UserToken;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.exceptions.InvalidActionTokenException;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues and consumes single-use account tokens (email verification, password reset).
 * The raw token exists only in the return value of {@link #issue}; the database holds its SHA-256 hex.
 * Per (user, purpose) limits: a cooldown between two issues and a cap per 24 hours.
 */
@Service
@RequiredArgsConstructor
public class UserTokenService {

    static final int TOKEN_BYTES = 32;
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final Duration CAP_WINDOW = Duration.ofHours(24);
    private static final int MAX_RAW_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository repository;
    private final MailProperties properties;
    private final Clock clock;

    /**
     * Creates a token for the user's current email address, consuming any older open token of the same
     * purpose, and returns the raw value (43 Base64url characters) that must go into the email.
     *
     * @throws TooManyRequestsException when the cooldown or the daily cap applies
     */
    // noRollbackFor: a rate-limit refusal must not mark a surrounding transaction (e.g. a profile
    // update that changes the email) rollback-only; the caller decides what a refusal means.
    @Transactional(noRollbackFor = TooManyRequestsException.class)
    public String issue(UserEntity user, UserTokenPurpose purpose, Duration ttl) {
        var now = Instant.now(clock);
        repository.deleteIssuedBefore(user.getId(), now.minus(RETENTION));
        enforceLimits(user, purpose, now);
        repository.consumeOpen(user.getId(), purpose, now);

        var bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(UserToken.builder()
                .user(user)
                .purpose(purpose)
                .tokenHash(hash(raw))
                .email(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build());
        return raw;
    }

    /**
     * Validates and burns a token. Every failure (unknown, wrong purpose, expired, already used, address
     * changed, lost a race) throws the same {@link InvalidActionTokenException}.
     */
    @Transactional
    public UserToken consume(String rawToken, UserTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_RAW_LENGTH) {
            throw new InvalidActionTokenException();
        }
        var now = Instant.now(clock);
        var token = repository.findByTokenHash(hash(rawToken.strip()))
                .filter(t -> t.getPurpose() == purpose)
                .orElseThrow(InvalidActionTokenException::new);
        var currentEmail = token.getUser().getEmail();
        if (token.getConsumedAt() != null
                || !token.getExpiresAt().isAfter(now)
                || currentEmail == null
                || !currentEmail.equalsIgnoreCase(token.getEmail())) {
            throw new InvalidActionTokenException();
        }
        if (repository.consumeIfOpen(token.getId(), now) != 1) {
            throw new InvalidActionTokenException();
        }
        token.setConsumedAt(now);
        return token;
    }

    /** Invalidates every open token of the user for the purpose (e.g. after an email change). */
    @Transactional
    public void consumeOutstanding(Integer userId, UserTokenPurpose purpose) {
        repository.consumeOpen(userId, purpose, Instant.now(clock));
    }

    /**
     * The cooldown protects one address from being flooded, so it only looks at tokens issued for the
     * user's current address (fixing a typo in the address right after registering still gets a mail).
     * The daily cap counts every token of the user for the purpose, whatever the address.
     */
    private void enforceLimits(UserEntity user, UserTokenPurpose purpose, Instant now) {
        var recent = repository.findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(user.getId(), purpose, now.minus(CAP_WINDOW));
        if (recent.isEmpty()) {
            return;
        }
        var cooldown = Duration.ofSeconds(properties.getTokens().getCooldownSeconds());
        var latestForAddress = recent.reversed().stream()
                .filter(t -> t.getEmail() != null && t.getEmail().equalsIgnoreCase(user.getEmail()))
                .map(UserToken::getIssuedAt)
                .findFirst();
        if (latestForAddress.isPresent() && latestForAddress.get().plus(cooldown).isAfter(now)) {
            throw new TooManyRequestsException("Please wait before requesting another email",
                    secondsUntil(latestForAddress.get().plus(cooldown), now));
        }
        if (recent.size() >= properties.getTokens().getMaxPerDay()) {
            var oldest = recent.get(0).getIssuedAt();
            throw new TooManyRequestsException("Daily limit of emails reached", secondsUntil(oldest.plus(CAP_WINDOW), now));
        }
    }

    private static long secondsUntil(Instant target, Instant now) {
        return Math.max(1, (Duration.between(now, target).toMillis() + 999) / 1000);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
