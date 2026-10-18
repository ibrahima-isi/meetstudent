package com.bowe.meetstudent.mail;

import com.bowe.meetstudent.entities.enums.ContactMailStatus;

import java.time.Instant;

/** Persists the result of a contact mail delivery run. Implementations must not throw for an unknown message id. */
public interface ContactMailStatusRecorder {

    /**
     * @param attempts real send attempts of this run (0 when mail is disabled)
     * @param sentAt   delivery time, only for {@link ContactMailStatus#SENT}
     */
    void record(Integer messageId, ContactMailStatus status, int attempts, Instant sentAt);
}
