package com.bowe.meetstudent.mail;

import com.bowe.meetstudent.config.ContactProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mails a stored contact message to the team once the transaction that saved it has committed, on the bounded
 * {@code mailExecutor}: SMTP latency or failure never reaches the HTTP response, and a rolled-back message is
 * never mailed. Logs only the message id and the exception class, never the address or the text.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContactEmailListener {

    private final EmailSender emailSender;
    private final ContactProperties properties;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContactMessageReceived(ContactMessageReceived event) {
        try {
            emailSender.send(ContactEmailTemplates.build(event, properties.effectiveRecipient()));
        } catch (Exception ex) {
            log.warn("Contact email failed messageId={} cause={}", event.messageId(), ex.getClass().getSimpleName());
        }
    }
}
