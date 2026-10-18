package com.bowe.meetstudent.integration.config;

import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.SmtpEmailSender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application with MAIL_PROVIDER presets and reads what Spring really resolved for
 * {@code spring.mail.*} and what the {@link JavaMailSender} was built with. Nothing connects to a provider: the
 * SMTP sender only opens a connection when a mail is sent.
 */
class MailProviderPresetIntegrationTests {

    private static final String TLS_REQUIRED = "spring.mail.properties.mail.smtp.starttls.required";

    @SpringBootTest(properties = {"app.mail.enabled=true", "app.mail.from=no-reply@example.com", "app.mail.provider=brevo"})
    @ActiveProfiles("test")
    @Nested
    class Brevo {
        @Autowired Environment environment;
        @Autowired JavaMailSender mailSender;
        @Autowired EmailSender emailSender;

        @Test
        void resolvesTheBrevoDefaults() {
            assertThat(emailSender).isInstanceOf(SmtpEmailSender.class);
            assertThat(environment.getProperty("spring.mail.host")).isEqualTo("smtp-relay.brevo.com");
            assertThat(environment.getProperty("spring.mail.port")).isEqualTo("587");
            var sender = (JavaMailSenderImpl) mailSender;
            assertThat(sender.getHost()).isEqualTo("smtp-relay.brevo.com");
            assertThat(sender.getPort()).isEqualTo(587);
            var properties = sender.getJavaMailProperties();
            assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
            assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
            // The timeouts of application.yml survive next to the preset.
            assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
        }
    }

    @SpringBootTest(properties = {"app.mail.enabled=true", "app.mail.from=no-reply@example.com", "app.mail.provider=gmail"})
    @ActiveProfiles("test")
    @Nested
    class Gmail {
        @Autowired JavaMailSender mailSender;

        @Test
        void resolvesTheGmailDefaults() {
            var sender = (JavaMailSenderImpl) mailSender;
            assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        }
    }

    @SpringBootTest(properties = {"app.mail.enabled=true", "app.mail.from=no-reply@example.com", "app.mail.provider=ses",
            "app.mail.ses-region=us-east-1"})
    @ActiveProfiles("test")
    @Nested
    class Ses {
        @Autowired JavaMailSender mailSender;

        @Test
        void resolvesTheSesDefaultsForTheConfiguredRegion() {
            var sender = (JavaMailSenderImpl) mailSender;
            assertThat(sender.getHost()).isEqualTo("email-smtp.us-east-1.amazonaws.com");
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
        }
    }

    @SpringBootTest(properties = {"app.mail.enabled=true", "app.mail.from=no-reply@example.com", "app.mail.provider=ses"})
    @ActiveProfiles("test")
    @Nested
    class SesDefaultRegion {
        @Autowired JavaMailSender mailSender;

        @Test
        void defaultsToIreland() {
            assertThat(((JavaMailSenderImpl) mailSender).getHost()).isEqualTo("email-smtp.eu-west-1.amazonaws.com");
        }
    }

    @SpringBootTest(properties = {"app.mail.enabled=true", "app.mail.from=no-reply@example.com", "app.mail.provider=brevo",
            "spring.mail.host=relay.example.org", "spring.mail.port=2525", "spring.mail.username=smtp-user",
            "spring.mail.properties.mail.smtp.starttls.required=false"})
    @ActiveProfiles("test")
    @Nested
    class ExplicitSettingsWin {
        @Autowired JavaMailSender mailSender;

        @Test
        void keepsTheExplicitValuesAndFillsTheRest() {
            var sender = (JavaMailSenderImpl) mailSender;
            assertThat(sender.getHost()).isEqualTo("relay.example.org");
            assertThat(sender.getPort()).isEqualTo(2525);
            assertThat(sender.getUsername()).isEqualTo("smtp-user");
            assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required")).isEqualTo("false");
            assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        }
    }

    @SpringBootTest(properties = {"app.mail.enabled=false", "app.mail.provider=brevo"})
    @ActiveProfiles("test")
    @Nested
    class DisabledMailIsUntouched {
        @Autowired Environment environment;
        @Autowired(required = false) JavaMailSender mailSender;
        @Autowired EmailSender emailSender;

        @Test
        void appliesNothingWhileMailIsDisabled() {
            assertThat(environment.getProperty("spring.mail.host")).isNull();
            assertThat(mailSender).isNull();
            assertThat(emailSender.isEnabled()).isFalse();
        }
    }
}
