package com.bowe.meetstudent.mail;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
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
    public SendOutcome send(EmailMessage message) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setValidateAddresses(true);
            helper.setFrom(from);
            helper.setTo(strictRecipient(message.to()));
            if (message.replyTo() != null) {
                helper.setReplyTo(strictRecipient(message.replyTo()));
            }
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
            log.info("Email sent type={} userId={}", message.type(), message.userId());
            return SendOutcome.SENT;
        } catch (Exception ex) {
            var outcome = SendOutcomeClassifier.classify(ex);
            // Type, user id, outcome and exception class only: the message can carry credentials or addresses.
            log.warn("Email not sent type={} userId={} outcome={} cause={}", message.type(), message.userId(), outcome,
                    ex.getClass().getSimpleName());
            return outcome;
        }
    }

    /** One plain RFC 822 mailbox: no lists, no groups, no loose syntax. */
    private static InternetAddress strictRecipient(String to) throws AddressException {
        var address = new InternetAddress(to, true);
        address.validate();
        if (address.isGroup()) {
            throw new AddressException("group recipient");
        }
        return address;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
