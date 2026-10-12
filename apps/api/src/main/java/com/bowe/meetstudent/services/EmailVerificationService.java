package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.exceptions.ServiceUnavailableException;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.mail.AccountEmailRequested;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;

/**
 * Optional email verification: unverified accounts keep working, verifying only flips
 * {@code emailVerified}. Sending is an event published inside the caller's transaction and handled
 * after commit, so a mail problem can never fail registration or a profile update.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final EmailSender emailSender;
    private final MailProperties properties;
    private final ApplicationEventPublisher events;

    /**
     * Issues a verification token and publishes the email request. Does nothing while email delivery is
     * disabled (no token is created for a mail that cannot be sent).
     *
     * @throws TooManyRequestsException when the per-account cooldown or daily cap applies
     */
    @Transactional
    public void sendVerification(UserEntity user, String lang) {
        if (!emailSender.isEnabled()) {
            return;
        }
        var raw = userTokenService.issue(user, UserTokenPurpose.EMAIL_VERIFICATION,
                Duration.ofHours(properties.getTokens().getVerifyTtlHours()));
        events.publishEvent(new AccountEmailRequested(EmailType.EMAIL_VERIFICATION, user.getId(), user.getEmail(),
                user.getFirstname(), normalizeLang(lang), raw));
    }

    /** Burns the token and marks its owner verified. Repeating it for a verified user is harmless. */
    @Transactional
    public void confirm(String rawToken) {
        var token = userTokenService.consume(rawToken, UserTokenPurpose.EMAIL_VERIFICATION);
        // Targeted update (not save): flushing the whole loaded row could overwrite a concurrent profile update.
        var userId = token.getUser().getId();
        if (userRepository.markEmailVerified(userId, token.getEmail()) == 0) {
            log.info("Email verification matched no account row userId={}", userId);
        }
    }

    /**
     * Sends a fresh verification email to the signed-in user.
     *
     * @throws ConflictException           already verified (409)
     * @throws ServiceUnavailableException email delivery disabled (503)
     * @throws TooManyRequestsException    cooldown or daily cap (429)
     */
    @Transactional
    public void resend(Integer userId, String lang) {
        var user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isEmailVerified()) {
            throw new ConflictException("Email address already verified");
        }
        if (!emailSender.isEnabled()) {
            throw new ServiceUnavailableException("Email delivery is not available");
        }
        sendVerification(user, lang);
    }

    private static String normalizeLang(String lang) {
        return lang != null && "en".equals(lang.strip().toLowerCase(Locale.ROOT)) ? "en" : "fr";
    }
}
