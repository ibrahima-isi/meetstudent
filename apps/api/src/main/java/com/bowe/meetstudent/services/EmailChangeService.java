package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.InvalidActionTokenException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.exceptions.ServiceUnavailableException;
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

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Changes an account's email address in two steps. The signed-in owner asks for a new address with their
 * current password; the address is only <em>pending</em> (an open {@code EMAIL_CHANGE} token bound to it) and
 * the old one keeps working for sign-in until the link mailed to the new address is opened. Nothing is stored
 * on the user row until then.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final MailProperties properties;
    private final ApplicationEventPublisher events;
    private final jakarta.persistence.EntityManager entityManager;

    /**
     * Starts (or replaces) the pending change and mails the confirmation link to {@code newEmail}.
     *
     * @throws FieldValidationException    wrong password ({@code currentPassword}) or not a change ({@code newEmail})
     * @throws ServiceUnavailableException email delivery is disabled, so the link could never arrive
     */
    @Transactional
    public void request(Integer userId, String newEmail, String currentPassword, String lang) {
        var user = load(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new FieldValidationException("currentPassword", "Mot de passe incorrect");
        }
        var address = newEmail.strip();
        if (address.equalsIgnoreCase(user.getEmail())) {
            throw new FieldValidationException("newEmail", "C'est déjà votre adresse e-mail");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(address, userId)) {
            throw new FieldValidationException("newEmail", "Cet email est déjà utilisé");
        }
        if (!emailSender.isEnabled()) {
            throw new ServiceUnavailableException("Email delivery is not available");
        }
        send(user, address, lang);
    }

    /** The address awaiting confirmation, if any. */
    @Transactional(readOnly = true)
    public Optional<String> pending(Integer userId) {
        load(userId);
        return userTokenService.openAddress(userId, UserTokenPurpose.EMAIL_CHANGE);
    }

    /** Drops the pending address; its link stops working. Harmless when nothing is pending. */
    @Transactional
    public void cancel(Integer userId) {
        load(userId);
        userTokenService.consumeOutstanding(userId, UserTokenPurpose.EMAIL_CHANGE);
    }

    /**
     * Mails a fresh link for the pending address (the previous link dies).
     *
     * @throws ConflictException           nothing is pending (409)
     * @throws ServiceUnavailableException email delivery is disabled (503)
     * @throws com.bowe.meetstudent.exceptions.TooManyRequestsException cooldown or daily cap (429)
     */
    @Transactional
    public void resend(Integer userId, String lang) {
        var user = load(userId);
        var address = userTokenService.openAddress(userId, UserTokenPurpose.EMAIL_CHANGE)
                .orElseThrow(() -> new ConflictException("No email change is pending"));
        if (!emailSender.isEnabled()) {
            throw new ServiceUnavailableException("Email delivery is not available");
        }
        send(user, address, lang);
    }

    /**
     * Burns the token and swaps the address, verified. A link whose address was taken by someone else in
     * the meantime is refused like any other bad link.
     */
    @Transactional
    public void confirm(String rawToken) {
        var token = userTokenService.consume(rawToken, UserTokenPurpose.EMAIL_CHANGE);
        var userId = token.getUser().getId();
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(token.getEmail(), userId)) {
            throw new InvalidActionTokenException();
        }
        // Targeted update (not save), like email verification, so a concurrent profile edit is not overwritten.
        if (userRepository.changeEmailAndRevokeSessions(userId, token.getEmail()) == 0) {
            throw new InvalidActionTokenException();
        }
        refreshTokenService.deleteByUserId(userId);
        // Any other pending link (and any verification link for the old address) is now meaningless.
        userTokenService.consumeOutstanding(userId, UserTokenPurpose.EMAIL_CHANGE);
        userTokenService.consumeOutstanding(userId, UserTokenPurpose.EMAIL_VERIFICATION);
        log.info("Email address changed userId={}", userId);
    }

    private void send(UserEntity user, String address, String lang) {
        var requestedVersion = user.getPasswordVersion();
        userRepository.findByIdForUpdate(user.getId()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Request/resend read the password or pending address before taking the lock.
        entityManager.refresh(user);
        if (user.getPasswordVersion() != requestedVersion) {
            throw new ConflictException("Credentials changed; start a new email change request");
        }
        var raw = userTokenService.issue(user, UserTokenPurpose.EMAIL_CHANGE,
                Duration.ofHours(properties.getTokens().getVerifyTtlHours()), address);
        events.publishEvent(new AccountEmailRequested(EmailType.EMAIL_CHANGE, user.getId(), address,
                user.getFirstname(), normalizeLang(lang), raw));
    }

    private UserEntity load(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static String normalizeLang(String lang) {
        return lang != null && "en".equals(lang.strip().toLowerCase(Locale.ROOT)) ? "en" : "fr";
    }
}
