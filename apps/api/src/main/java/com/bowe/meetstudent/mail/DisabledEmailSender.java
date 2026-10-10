package com.bowe.meetstudent.mail;

import lombok.extern.slf4j.Slf4j;

/** Default sender: nothing is configured, so nothing leaves the process. Logs no address and no link. */
@Slf4j
public class DisabledEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.info("Email sending is disabled (MAIL_ENABLED=false); skipped {} for user {}", message.type(), message.userId());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
