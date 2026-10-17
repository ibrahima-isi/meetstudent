package com.bowe.meetstudent.mail;

/**
 * A fully rendered email, ready to hand to an {@link EmailSender}.
 * The recipient and subject are checked for line breaks here so header injection is impossible
 * whichever sender is used.
 */
public record EmailMessage(EmailType type, Integer userId, String to, String subject, String text, String html, String replyTo) {

    public EmailMessage {
        if (to == null || to.isBlank() || hasLineBreak(to)) {
            throw new IllegalArgumentException("Invalid email recipient");
        }
        if (subject != null && hasLineBreak(subject)) {
            throw new IllegalArgumentException("Invalid email subject");
        }
        if (replyTo != null && hasLineBreak(replyTo)) {
            throw new IllegalArgumentException("Invalid email reply-to");
        }
    }

    /** Message without a Reply-To header (all account emails). */
    public EmailMessage(EmailType type, Integer userId, String to, String subject, String text, String html) {
        this(type, userId, to, subject, text, html, null);
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    /** Never prints the recipient or the body (the body carries a one-time link). */
    @Override
    public String toString() {
        return "EmailMessage[type=" + type + ", userId=" + userId + "]";
    }
}
