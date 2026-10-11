package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.mail.AccountEmailRequested;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Password reset by emailed single-use token. {@link #request} must be indistinguishable from the outside
 * whether the address belongs to an account, a limit applies or email delivery is disabled: it never throws
 * for those cases and returns nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only handles 72 bytes and the encoder throws beyond that. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final MailProperties properties;
    private final ApplicationEventPublisher events;

    /**
     * Sends a reset link when the address belongs to an account and the limits allow it; otherwise does
     * nothing. An unknown address (or disabled mail) does one lookup and one hash, which is only a rough
     * stand-in: a known address also issues a token (several queries) so it stays a few milliseconds slower.
     * That difference is the residual timing oracle; Traefik's per-IP rate limit bounds how often it can be probed.
     */
    @Transactional
    public void request(String email, String lang) {
        // A list on purpose: case-variant duplicates must not throw (that would be a 500 and an oracle).
        var users = userRepository.findAllByEmailIgnoreCaseOrderByIdAsc(email);
        if (users.isEmpty() || !emailSender.isEnabled()) {
            dummyWork();
            return;
        }
        var user = users.get(0);
        try {
            var raw = userTokenService.issue(user, UserTokenPurpose.PASSWORD_RESET,
                    Duration.ofMinutes(properties.getTokens().getResetTtlMinutes()));
            events.publishEvent(new AccountEmailRequested(EmailType.PASSWORD_RESET, user.getId(), user.getEmail(),
                    user.getFirstname(), normalizeLang(lang), raw));
        } catch (TooManyRequestsException ex) {
            log.info("Password reset email skipped (rate limit) userId={}", user.getId());
        }
    }

    /**
     * Sets a new password. Checks that need no token come first so a typo does not burn the link; then, in one
     * transaction: consume the token, write the password, burn the other reset links, end every refresh-token
     * session and mark the address verified (the link proved ownership of the mailbox).
     * Access tokens already issued stay valid until they expire.
     */
    @Transactional
    public void confirm(String rawToken, String password, String confirmedPassword) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new FieldValidationException("password", "Le mot de passe doit contenir au moins 8 caractères");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new FieldValidationException("password", "Le mot de passe ne doit pas dépasser 72 octets");
        }
        if (!password.equals(confirmedPassword)) {
            throw new FieldValidationException("confirmedPassword", "Les mots de passe ne correspondent pas");
        }
        var token = userTokenService.consume(rawToken, UserTokenPurpose.PASSWORD_RESET);
        var userId = token.getUser().getId();
        // Targeted writes (not save): flushing a whole loaded row could overwrite a concurrent profile update.
        userRepository.updatePassword(userId, passwordEncoder.encode(password));
        userTokenService.consumeOutstanding(userId, UserTokenPurpose.PASSWORD_RESET);
        refreshTokenService.deleteByUserId(userId);
        userRepository.markEmailVerified(userId, token.getEmail());
    }

    /** Cheap placeholder (a random value and a SHA-256); it narrows, but does not remove, the timing gap. */
    private static void dummyWork() {
        UserTokenService.hash(UUID.randomUUID().toString());
    }

    private static String normalizeLang(String lang) {
        return lang != null && "en".equals(lang.strip().toLowerCase(Locale.ROOT)) ? "en" : "fr";
    }
}
