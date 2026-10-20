package com.bowe.meetstudent.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the Expert approval mails once the transaction that recorded the status has committed, on the bounded
 * {@code mailExecutor}: a send failure never reaches the registration or the admin decision, which are already saved.
 * A refused delivery is logged by the sender; here only the exception class is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpertEmailListener {

    private final EmailSender emailSender;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExpertEmailRequested(ExpertEmailRequested event) {
        try {
            // SmtpEmailSender already logs a failed delivery (type, userId, outcome): not repeated here.
            emailSender.send(ExpertEmailTemplates.build(event));
        } catch (Exception ex) {
            // Class only: messages can carry addresses or credentials.
            log.warn("Expert email failed type={} userId={} cause={}", event.type(), event.userId(), ex.getClass().getSimpleName());
        }
    }
}
