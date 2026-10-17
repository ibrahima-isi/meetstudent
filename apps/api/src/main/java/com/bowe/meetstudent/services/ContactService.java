package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.ContactRequest;
import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.mail.ContactMessageReceived;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Service
public class ContactService {

    private final ContactMessageRepository repository;
    private final ContactRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ContactService(ContactMessageRepository repository, ContactRateLimiter rateLimiter,
                          ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Counts the attempt against the client's budget (honeypot hits included, a refusal throws
     * {@code TooManyRequestsException} before anything is written), then drops a honeypot hit silently or stores
     * the message and publishes the event that mails it after commit. Only the store runs in a transaction: a
     * refusal or a honeypot hit never takes a database connection.
     */
    public void submit(ContactRequest request, String clientIp) {
        rateLimiter.check(clientIp);
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            return;
        }
        transaction.executeWithoutResult(status -> store(request));
    }

    private void store(ContactRequest request) {
        var saved = repository.save(ContactMessage.builder()
                .senderEmail(request.getEmail().strip())
                .subject(request.getSubject().strip())
                .body(request.getMessage().strip())
                .language(request.getLanguage())
                .createdAt(clock.instant())
                .build());
        events.publishEvent(new ContactMessageReceived(saved.getId(), saved.getSenderEmail(), saved.getSubject(),
                saved.getBody(), saved.getLanguage()));
    }
}
