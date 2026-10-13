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
    private final AddressIssueLock addressLock;
    private final com.bowe.meetstudent.repositories.UserRepository userRepository;

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
        return issue(user, purpose, ttl, user.getEmail());
    }

    /**
     * Same as {@link #issue(UserEntity, UserTokenPurpose, Duration)} for a token bound to {@code address}
     * instead of the user's current one (an email change: the link goes to the address being claimed).
     * The cooldown and the caps then apply to that address.
     */
    @Transactional(noRollbackFor = TooManyRequestsException.class)
    public String issue(UserEntity user, UserTokenPurpose purpose, Duration ttl, String address) {
        // Same order as credential changes: user, address, then action-token rows.
        userRepository.findByIdForUpdate(user.getId()).orElseThrow(InvalidActionTokenException::new);
        addressLock.acquire(address);
        var now = Instant.now(clock);
        repository.deleteIssuedBefore(now.minus(RETENTION));
        enforceLimits(user, purpose, address, now);
        enforceAddressCap(address, purpose, now);
        // A reset request must not kill a link the owner already holds: anyone can ask for one, and the
        // per-address cap bounds how many exist. A successful reset burns all the others. Verification
        // and email-change links are replaced as before.
        if (purpose != UserTokenPurpose.PASSWORD_RESET) {
            repository.consumeOpen(user.getId(), purpose, now);
        }

        var bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(UserToken.builder()
                .user(user)
                .purpose(purpose)
                .tokenHash(hash(raw))
                .email(address)
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
        var tokenHash = hash(rawToken.strip());
        var ownerId = repository.findUserIdByTokenHash(tokenHash).orElseThrow(InvalidActionTokenException::new);
        userRepository.findByIdForUpdate(ownerId).orElseThrow(InvalidActionTokenException::new);
        // Read only after the owner lock: a password change may have consumed it while we waited.
        var token = repository.findByTokenHash(tokenHash)
                .filter(t -> t.getPurpose() == purpose)
                .orElseThrow(InvalidActionTokenException::new);
        if (token.getUser() == null) { // account deleted: the row only remains as cap evidence
            throw new InvalidActionTokenException();
        }
        var currentEmail = token.getUser().getEmail();
        // An email-change token is bound to the address being claimed, which by design is not the current one.
        var addressStillMatches = purpose == UserTokenPurpose.EMAIL_CHANGE
                || (currentEmail != null && currentEmail.equalsIgnoreCase(token.getEmail()));
        if (token.getConsumedAt() != null
                || !token.getExpiresAt().isAfter(now)
                || !addressStillMatches) {
            throw new InvalidActionTokenException();
        }
        if (repository.consumeIfOpen(token.getId(), now) != 1) {
            throw new InvalidActionTokenException();
        }
        token.setConsumedAt(now);
        return token;
    }

    /** The address the newest open token of the purpose was issued for, if any (the pending email change). */
    @Transactional(readOnly = true)
    public java.util.Optional<String> openAddress(Integer userId, UserTokenPurpose purpose) {
        return repository.findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(
                userId, purpose, Instant.now(clock)).map(UserToken::getEmail);
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
    private void enforceLimits(UserEntity user, UserTokenPurpose purpose, String address, Instant now) {
        var recent = repository.findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(user.getId(), purpose, now.minus(CAP_WINDOW));
        if (recent.isEmpty()) {
            return;
        }
        var cooldown = Duration.ofSeconds(properties.getTokens().getCooldownSeconds());
        var latestForAddress = recent.reversed().stream()
                .filter(t -> t.getEmail() != null && t.getEmail().equalsIgnoreCase(address))
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

    /**
     * Mail-bombing guard: at most {@code max-per-address-per-day} tokens per address per purpose in 24 h, counted
     * case-insensitively across every user (deleted accounts included, their rows are kept), so several accounts cannot aim at one third party.
     */
    private void enforceAddressCap(String email, UserTokenPurpose purpose, Instant now) {
        var forAddress = repository.findAddressTokens(email, purpose, now.minus(CAP_WINDOW));
        if (forAddress.size() >= properties.getTokens().getMaxPerAddressPerDay()) {
            throw new TooManyRequestsException("Too many emails for this address", secondsUntil(forAddress.get(0).getIssuedAt().plus(CAP_WINDOW), now));
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
