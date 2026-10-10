package com.bowe.meetstudent.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * Renders and sends account emails once the transaction that created the token has committed, on the
 * bounded {@code mailExecutor}, so SMTP latency and failures never reach the HTTP response.
 * {@code fallbackExecution} keeps an event published outside any transaction from being silently dropped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEmailListener {

    private final EmailSender emailSender;
    private final MailProperties properties;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountEmailRequested(AccountEmailRequested event) {
        try {
            var ttl = event.type() == EmailType.PASSWORD_RESET
                    ? Duration.ofMinutes(properties.getTokens().getResetTtlMinutes())
                    : Duration.ofHours(properties.getTokens().getVerifyTtlHours());
            emailSender.send(EmailTemplates.build(event, properties.getFrontendBaseUrl(), ttl));
        } catch (Exception ex) {
            // Class only: messages can carry addresses or credentials.
            log.warn("Account email failed type={} userId={} cause={}", event.type(), event.userId(), ex.getClass().getSimpleName());
        }
    }
}
