package com.bowe.meetstudent.mail;

/**
 * Published (inside the transaction that stored the message) when a contact message must be mailed to the team.
 * {@link #toString()} redacts the sender and the text.
 *
 * @param lang {@code fr} or {@code en}, or null when the visitor did not say
 */
public record ContactMessageReceived(Integer messageId, String senderEmail, String subject, String message, String lang) {

    @Override
    public String toString() {
        return "ContactMessageReceived[messageId=" + messageId + "]";
    }
}
