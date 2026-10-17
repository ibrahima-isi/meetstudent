package com.bowe.meetstudent.dto;

import java.time.Instant;

/** A contact message as the back office sees it: no internal field beyond the ones listed. */
public record AdminMessageDTO(Integer id, String email, String subject, String message, String language,
                              Instant createdAt) {
}
