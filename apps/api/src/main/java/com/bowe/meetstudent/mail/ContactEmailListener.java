package com.bowe.meetstudent.mail;

import com.bowe.meetstudent.config.ContactProperties;
import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;

/**
 * Mails a stored contact message to the team once the transaction that saved it has committed, on the dedicated
 * bounded {@code contactMailExecutor} (a flood of contact messages can never delay a verification or reset mail).
 * Transient failures are retried here with a backoff ({@code app.contact.mail.retry.*}); a permanent one (bad
 * credentials, refused address) or a disabled mailer is not. The result is saved on the message row. Nothing here
 * can fail the HTTP response, and only the message id, the outcome and exception classes are logged: never the
 * address or the text.
 */
@Slf4j
@Component
public class ContactEmailListener {

    private final EmailSender emailSender;
    private final ContactProperties properties;
    private final ContactMailStatusRecorder recorder;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public ContactEmailListener(EmailSender emailSender, ContactProperties properties, ContactMailStatusRecorder recorder, Clock clock) {
        this(emailSender, properties, recorder, clock, Sleeper.THREAD);
    }

    public ContactEmailListener(EmailSender emailSender, ContactProperties properties, ContactMailStatusRecorder recorder,
                                Clock clock, Sleeper sleeper) {
        this.emailSender = emailSender;
        this.properties = properties;
        this.recorder = recorder;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Async("contactMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContactMessageReceived(ContactMessageReceived event) {
        var retry = properties.getMail().getRetry();
        var max = retry.effectiveMaxAttempts();
        var outcome = SendOutcome.FAILED;
        var attempts = 0;
        try {
            var message = ContactEmailTemplates.build(event, properties.effectiveRecipient());
            while (attempts < max) {
                attempts++;
                outcome = sendOnce(message, event);
                if (!outcome.isTransient() || attempts == max) {
                    break;
                }
                if (!pause(retry.delayAfter(attempts))) {
                    break;
                }
            }
        } catch (Exception ex) {
            // The template or the recipient setting is unusable: nothing to retry.
            log.warn("Contact email not built messageId={} cause={}", event.messageId(), ex.getClass().getSimpleName());
            outcome = SendOutcome.FAILED;
        }
        if (outcome == SendOutcome.DISABLED || outcome == SendOutcome.LOGGED) {
            attempts = 0;
        }
        log.info("Contact email delivery messageId={} outcome={} attempts={}", event.messageId(), outcome, attempts);
        record(event, outcome, attempts);
    }

    private SendOutcome sendOnce(EmailMessage message, ContactMessageReceived event) {
        try {
            var outcome = emailSender.send(message);
            return outcome == null ? SendOutcome.FAILED : outcome;
        } catch (Exception ex) {
            // Senders are not supposed to throw; if one does, only the class is logged.
            log.warn("Contact email failed messageId={} cause={}", event.messageId(), ex.getClass().getSimpleName());
            return SendOutcome.FAILED;
        }
    }

    /**
     * @return false when the thread was interrupted: the remaining attempts are given up and the row is saved with
     * what happened so far. The executor stops with an orderly {@code shutdown()}, which does not interrupt: it lets
     * running and queued deliveries continue for up to {@code awaitTerminationSeconds} (10 s). Whatever is still
     * running after that dies with the JVM and its row stays PENDING until it can be resent (pending timeout).
     */
    private boolean pause(Duration delay) {
        try {
            sleeper.sleep(delay);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void record(ContactMessageReceived event, SendOutcome outcome, int attempts) {
        var status = switch (outcome) {
            case SENT -> ContactMailStatus.SENT;
            case DISABLED, LOGGED -> ContactMailStatus.SKIPPED;
            default -> ContactMailStatus.FAILED;
        };
        try {
            recorder.record(event.messageId(), status, attempts, status == ContactMailStatus.SENT ? clock.instant() : null);
        } catch (Exception ex) {
            log.warn("Contact email status not saved messageId={} cause={}", event.messageId(), ex.getClass().getSimpleName());
        }
    }
}
