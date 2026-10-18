package com.bowe.meetstudent.services;

import com.bowe.meetstudent.config.ContactProperties;
import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.ServiceUnavailableException;
import com.bowe.meetstudent.mail.ContactMessageReceived;
import com.bowe.meetstudent.mail.EmailSender;
import org.springframework.context.ApplicationEventPublisher;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AdminMessageService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ContactMessageRepository repository;
    private final EmailSender emailSender;
    private final ApplicationEventPublisher events;
    private final ContactProperties properties;
    private final Clock clock;

    /** Fixed order (newest first, id desc as tie-break): any requested sort is ignored, the size is capped. */
    @Transactional(readOnly = true)
    public Page<ContactMessage> list(Pageable requested) {
        var size = Math.min(requested.getPageSize(), MAX_PAGE_SIZE);
        return repository.findAll(PageRequest.of(requested.getPageNumber(), size, NEWEST_FIRST));
    }

    @Transactional
    public void delete(int id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Message not found");
        }
        repository.deleteById(id);
    }

    /**
     * Queues the team mail of a stored message again: the delivery state goes back to PENDING and the usual event is
     * published, so the after-commit listener (own executor, retries, saved result) does the sending. The state change
     * is one conditional UPDATE, so a double click or a loop cannot queue duplicates, fill the queue or let an older
     * attempt overwrite a newer one: while a delivery is in flight (PENDING) the answer is 409. A row that has been
     * PENDING longer than {@code app.contact.mail.pending-timeout} is treated as lost and may be resent.
     * Checks run in this order: 404, 503, 409.
     *
     * @throws ResourceNotFoundException   unknown message (404)
     * @throws ServiceUnavailableException mail is disabled (503)
     * @throws ConflictException           a delivery is already queued or running (409)
     */
    @Transactional
    public void resend(int id) {
        var message = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!emailSender.isEnabled()) {
            throw new ServiceUnavailableException("Email delivery is not available");
        }
        var now = clock.instant();
        var staleBefore = now.minus(properties.getMail().effectivePendingTimeout());
        if (repository.claimResend(id, now, staleBefore) == 0) {
            throw new ConflictException("An email delivery of this message is already in progress");
        }
        events.publishEvent(new ContactMessageReceived(message.getId(), message.getSenderEmail(), message.getSubject(),
                message.getBody(), message.getLanguage()));
    }
}
