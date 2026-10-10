package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.mail.AccountEmailListener;
import com.bowe.meetstudent.mail.AccountEmailRequested;
import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.support.CapturingEmailSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AccountEmailListenerTest {

    private final MailProperties properties = new MailProperties();

    {
        properties.setFrontendBaseUrl("https://app.example.com");
    }

    @Test
    void rendersTheMessageAndHandsItToTheSender() {
        var capture = new CapturingEmailSender();
        var listener = new AccountEmailListener(capture, properties);

        listener.onAccountEmailRequested(new AccountEmailRequested(EmailType.PASSWORD_RESET, 3, "a@example.com", "Awa", "en", "tok_123-A"));

        assertThat(capture.all()).hasSize(1);
        assertThat(CapturingEmailSender.extractToken(capture.last())).isEqualTo("tok_123-A");
        assertThat(capture.last().text()).contains("https://app.example.com/en/reset-password#token=tok_123-A");
        assertThat(capture.last().text()).contains("1 hour");
    }

    @Test
    void verificationUsesTheConfiguredTtl() {
        properties.getTokens().setVerifyTtlHours(48);
        var capture = new CapturingEmailSender();
        new AccountEmailListener(capture, properties)
                .onAccountEmailRequested(new AccountEmailRequested(EmailType.EMAIL_VERIFICATION, 3, "a@example.com", "Awa", "en", "t"));
        assertThat(capture.last().text()).contains("48 hours");
    }

    @Test
    void aFailingSenderOrTemplateNeverPropagates() {
        EmailSender boom = new EmailSender() {
            @Override
            public void send(EmailMessage message) {
                throw new IllegalStateException("smtp exploded");
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
        var listener = new AccountEmailListener(boom, properties);
        assertThatCode(() -> listener.onAccountEmailRequested(
                new AccountEmailRequested(EmailType.PASSWORD_RESET, 3, "a@example.com", "Awa", "fr", "t"))).doesNotThrowAnyException();
        assertThatCode(() -> listener.onAccountEmailRequested(
                new AccountEmailRequested(EmailType.PASSWORD_RESET, 3, "bad\r\n@example.com", "Awa", "fr", "t"))).doesNotThrowAnyException();
    }

    @Test
    void theEventNeverPrintsItsTokenOrAddress() {
        var event = new AccountEmailRequested(EmailType.PASSWORD_RESET, 3, "a@example.com", "Awa", "fr", "SECRETTOKEN");
        assertThat(event.toString()).doesNotContain("SECRETTOKEN").doesNotContain("a@example.com");
    }

    @Test
    void theWarningLogNeverContainsTheExceptionMessageOrTheAddress() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AccountEmailListener.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            EmailSender boom = new EmailSender() {
                @Override
                public void send(EmailMessage message) {
                    throw new IllegalStateException("password=hunter2 for victim@example.com");
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
            };
            var listener = new AccountEmailListener(boom, properties);
            listener.onAccountEmailRequested(new AccountEmailRequested(EmailType.PASSWORD_RESET, 9, "victim@example.com", "Awa", "fr", "SECRETTOKEN"));
            listener.onAccountEmailRequested(new AccountEmailRequested(EmailType.PASSWORD_RESET, 9, "victim@example.com\r\nBcc: x@y.z", "Awa", "fr", "SECRETTOKEN"));
            assertThat(logs.list).isNotEmpty();
            logs.list.forEach(e -> {
                assertThat(e.getThrowableProxy()).isNull();
                assertThat(e.getFormattedMessage()).contains("PASSWORD_RESET").contains("9")
                        .doesNotContain("hunter2").doesNotContain("victim@example.com").doesNotContain("x@y.z").doesNotContain("SECRETTOKEN");
            });
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void aTrailingSpaceInTheConfiguredBaseUrlStillGivesACleanLink() {
        var props = new MailProperties();
        props.setFrontendBaseUrl("https://app.example.com ");
        var capture = new CapturingEmailSender();
        new AccountEmailListener(capture, props)
                .onAccountEmailRequested(new AccountEmailRequested(EmailType.PASSWORD_RESET, 3, "a@example.com", "Awa", "en", "t"));
        assertThat(capture.last().text()).contains("https://app.example.com/en/reset-password#token=t").doesNotContain(" /en");
    }
}
