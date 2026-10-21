package com.bowe.meetstudent.mail;

/**
 * Published (inside the transaction that changed the Expert status, or right after registration) when the teacher
 * must be told about their approval request. {@link #toString()} redacts the address, the name and the reason.
 *
 * @param type   one of {@code EXPERT_REQUEST_RECEIVED}, {@code EXPERT_APPROVED}, {@code EXPERT_REJECTED}
 * @param lang   {@code fr} or {@code en}; anything else is treated as {@code fr}
 * @param reason rejection reason (already validated by {@code SafeText}); {@code null} for the other types or when none
 */
public record ExpertEmailRequested(EmailType type, Integer userId, String to, String firstName, String lang, String reason) {

    @Override
    public String toString() {
        return "ExpertEmailRequested[type=" + type + ", userId=" + userId + "]";
    }
}
