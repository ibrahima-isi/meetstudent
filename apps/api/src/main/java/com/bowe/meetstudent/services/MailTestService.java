package com.bowe.meetstudent.services;

import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.mail.MailTestEmailTemplates;
import com.bowe.meetstudent.mail.SendOutcome;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends one test mail, synchronously and through the real {@link EmailSender}, to the signed-in admin's own address
 * and nobody else's, so the SMTP settings can be checked from the back office. The result is only the outcome: no
 * address, host, credential or exception message is returned or logged.
 */
@Slf4j
@Service
public class MailTestService {

    private static final int PRUNE_ABOVE = 1_000;

    private final EmailSender emailSender;
    private final UserRepository userRepository;
    private final MailProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<Integer, Instant> lastAttempt = new ConcurrentHashMap<>();

    public MailTestService(EmailSender emailSender, UserRepository userRepository, MailProperties properties, Clock clock) {
        this.emailSender = emailSender;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return the outcome ({@link SendOutcome#DISABLED} without sending when mail is switched off)
     * @throws TooManyRequestsException  another test of the same admin is still within the cooldown (429)
     * @throws ResourceNotFoundException the admin account no longer exists
     */
    public SendOutcome sendTestTo(Integer adminId) {
        if (!emailSender.isEnabled()) {
            return SendOutcome.DISABLED;
        }
        acquireCooldown(adminId);
        var admin = userRepository.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SendOutcome outcome;
        try {
            outcome = emailSender.send(MailTestEmailTemplates.build(adminId, admin.getEmail()));
        } catch (Exception ex) {
            outcome = SendOutcome.FAILED;
        }
        if (outcome == null) {
            outcome = SendOutcome.FAILED;
        }
        log.info("Mail test adminId={} outcome={}", adminId, outcome);
        return outcome;
    }

    private void acquireCooldown(Integer adminId) {
        var cooldown = Duration.ofSeconds(Math.max(0, properties.getMail().getTestCooldownSeconds()));
        if (cooldown.isZero()) {
            return;
        }
        var now = clock.instant();
        var retryAfter = new long[1];
        lastAttempt.compute(adminId, (id, previous) -> {
            if (previous != null && now.isBefore(previous.plus(cooldown))) {
                retryAfter[0] = Duration.between(now, previous.plus(cooldown)).toSeconds() + 1;
                return previous;
            }
            return now;
        });
        if (retryAfter[0] > 0) {
            throw new TooManyRequestsException("A mail test was just sent, wait a few seconds", retryAfter[0]);
        }
        if (lastAttempt.size() > PRUNE_ABOVE) {
            lastAttempt.values().removeIf(at -> !now.isBefore(at.plus(cooldown)));
        }
    }
}
