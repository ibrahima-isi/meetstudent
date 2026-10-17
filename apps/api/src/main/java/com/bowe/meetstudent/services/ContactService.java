package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.ContactRequest;
import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.mail.ContactMessageReceived;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactMessageRepository repository;
    private final ContactRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Counts the attempt against the client's budget (honeypot hits included, a refusal throws
     * {@code TooManyRequestsException} before anything is written), then drops a honeypot hit silently or stores
     * the message and publishes the event that mails it after commit.
     */
    @Transactional
    public void submit(ContactRequest request, String clientIp) {
        rateLimiter.check(clientIp);
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            return;
        }
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
