package com.bowe.meetstudent.unit.mail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bowe.meetstudent.mail.DevLogEmailSender;
import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.EmailType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class DevLogEmailSenderTest {

    @Test
    void lineBreaksInTheBodyCannotForgeLogLines() {
        var logger = (Logger) LoggerFactory.getLogger(DevLogEmailSender.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            new DevLogEmailSender().send(new EmailMessage(EmailType.PASSWORD_RESET, 1, "a@example.com", "s",
                    "Bonjour Awa\r\n2026-01-01 INFO forged line\nhttps://app.example.com/fr/reset-password#token=t", "<p/>"));
            assertThat(logs.list).hasSize(1);
            var msg = logs.list.get(0).getFormattedMessage();
            assertThat(msg).doesNotContain("\n").doesNotContain("\r");
            assertThat(msg).contains("#token=t").contains("forged line");
        } finally {
            logger.detachAppender(logs);
        }
    }
}
