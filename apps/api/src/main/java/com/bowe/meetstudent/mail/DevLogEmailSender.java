package com.bowe.meetstudent.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Local development only: writes the whole message, link included, to the log so the flows can be
 * exercised without an SMTP server. {@link MailConfig} refuses to start with it under the prod profile.
 */
@Slf4j
public class DevLogEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        // Line breaks (e.g. from a first name) must not be able to forge extra log lines.
        log.info("DEV EMAIL {} for user {} to {} | {} | {}", message.type(), message.userId(), message.to(),
                message.subject(), oneLine(message.text()));
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " / ");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
