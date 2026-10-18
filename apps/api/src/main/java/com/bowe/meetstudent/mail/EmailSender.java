package com.bowe.meetstudent.mail;

/**
 * Port for outgoing account emails. Implementations return a {@link SendOutcome} and must never throw to the caller and must never log
 * a token, a link, a recipient address or an exception message (they can carry credentials).
 */
public interface EmailSender {

    SendOutcome send(EmailMessage message);

    /** True when messages are really delivered (or, in local dev, written to the log). */
    boolean isEnabled();
}
