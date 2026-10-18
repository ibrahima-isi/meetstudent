package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import com.bowe.meetstudent.mail.ContactMailStatusRecorder;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Saves the outcome of a contact mail delivery run. {@code REQUIRES_NEW}: the listener runs after the commit of the
 * transaction that stored the message (whose resources are still bound to the thread when the executor is
 * synchronous), so the update must never join, or be rolled back with, a caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class ContactMailStatusService implements ContactMailStatusRecorder {

    private final ContactMessageRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Integer messageId, ContactMailStatus status, int attempts, Instant sentAt) {
        // Targeted update: a message deleted meanwhile simply matches no row.
        repository.updateMailDelivery(messageId, status, attempts, sentAt);
    }
}
