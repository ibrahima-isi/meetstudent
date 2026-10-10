package com.bowe.meetstudent.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Sends through SMTP. Never throws and logs only the email type, the user id and the exception class:
 * never the token, the link, the address or the exception message, which can carry credentials.
 */
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
            log.info("Email sent type={} userId={}", message.type(), message.userId());
        } catch (Exception ex) {
            log.warn("Email not sent type={} userId={} cause={}", message.type(), message.userId(), ex.getClass().getSimpleName());
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
