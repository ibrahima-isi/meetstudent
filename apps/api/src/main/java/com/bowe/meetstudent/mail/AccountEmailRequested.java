package com.bowe.meetstudent.mail;

/**
 * Published (inside the transaction that created the token) when an account email must be sent.
 * Carries the raw one-time token in memory only; {@link #toString()} redacts it and the address.
 *
 * @param lang {@code fr} or {@code en}; anything else is treated as {@code fr}
 */
public record AccountEmailRequested(EmailType type, Integer userId, String to, String firstName, String lang, String token) {

    @Override
    public String toString() {
        return "AccountEmailRequested[type=" + type + ", userId=" + userId + "]";
    }
}
