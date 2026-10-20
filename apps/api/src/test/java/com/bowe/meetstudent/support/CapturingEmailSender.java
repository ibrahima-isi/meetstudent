package com.bowe.meetstudent.support;

import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.SendOutcome;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test double that records every message instead of sending it. */
public class CapturingEmailSender implements EmailSender {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

    private volatile SendOutcome outcome = SendOutcome.SENT;
    private volatile RuntimeException failure;

    @Override
    public SendOutcome send(EmailMessage message) {
        messages.add(message);
        if (failure != null) {
            throw failure;
        }
        return outcome;
    }

    /** Makes every following send report this outcome (the message is still recorded). */
    public void respondWith(SendOutcome outcome) {
        this.outcome = outcome;
    }

    /** Makes every following send throw this exception (the message is still recorded). */
    public void throwOnSend(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public List<EmailMessage> all() {
        return List.copyOf(messages);
    }

    public EmailMessage last() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public void clear() {
        messages.clear();
        outcome = SendOutcome.SENT;
        failure = null;
    }

    /** Pulls the token out of the link in the plain-text body. */
    public static String extractToken(EmailMessage message) {
        Matcher matcher = TOKEN.matcher(message.text());
        if (!matcher.find()) {
            throw new IllegalStateException("no token link in message");
        }
        return matcher.group(1);
    }
}
