package com.bowe.meetstudent.dto;

import java.time.Instant;

/**
 * A contact message as the back office sees it: no internal field beyond the ones listed.
 *
 * @param mailStatus   UNKNOWN (before delivery tracking), PENDING, SENT, FAILED or SKIPPED (mail disabled)
 * @param mailAttempts send attempts of the latest delivery run
 * @param mailSentAt   when the team mail was accepted by the SMTP server, null otherwise
 */
public record AdminMessageDTO(Integer id, String email, String subject, String message, String language,
                              Instant createdAt, String mailStatus, int mailAttempts, Instant mailSentAt) {
}
