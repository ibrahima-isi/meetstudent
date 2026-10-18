package com.bowe.meetstudent.entities.enums;

/** Where the team mail of a contact message stands. Stored as text in {@code contact_messages.mail_status} (16 chars max). */
public enum ContactMailStatus {
    /** Rows that existed before delivery tracking (V27). */
    UNKNOWN,
    /** Queued or being sent. */
    PENDING,
    SENT,
    /** Every attempt failed or the failure is permanent (bad credentials, refused address). */
    FAILED,
    /** Mail is disabled (MAIL_ENABLED=false): nothing was attempted. */
    SKIPPED
}
