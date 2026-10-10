package com.bowe.meetstudent.unit.mail;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.SmtpEmailSender;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTest {

    private static final String SECRET_TOKEN = "SECRETTOKEN123";
    private static final String ADDRESS = "victim@example.com";

    private final JavaMailSender javaMailSender = Mockito.mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(javaMailSender, "no-reply@example.com");
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;
    private Level previousLevel;

    private final EmailMessage message = new EmailMessage(EmailType.EMAIL_VERIFICATION, 42, ADDRESS,
            "Sujet", "Bonjour\nhttps://app.example.com/fr/verify-email#token=" + SECRET_TOKEN,
            "<p>Bonjour <a href=\"https://app.example.com/fr/verify-email#token=" + SECRET_TOKEN + "\">lien</a></p>");

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        logs.start();
        logger.addAppender(logs);
        when(javaMailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logs);
        logger.setLevel(previousLevel);
    }

    @Test
    void sendsAMultipartMessageFromTheConfiguredAddress() throws Exception {
        sender.send(message);

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        var sent = captor.getValue();
        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("no-reply@example.com");
        assertThat(((InternetAddress) sent.getAllRecipients()[0]).getAddress()).isEqualTo(ADDRESS);
        assertThat(sent.getSubject()).isEqualTo("Sujet");
        assertThat(sent.getContent()).isInstanceOf(Multipart.class);
        sent.saveChanges(); // what the transport does before writing: fixes the content types
        var leaves = new ArrayList<String>();
        collect((Part) sent, leaves);
        assertThat(leaves).anyMatch(s -> s.startsWith("text/plain") && s.contains("Bonjour"));
        assertThat(leaves).anyMatch(s -> s.startsWith("text/html") && s.contains("<a href"));
    }

    private static void collect(Part part, List<String> out) throws Exception {
        if (part.isMimeType("multipart/*")) {
            var mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                collect(bp, out);
            }
        } else {
            out.add(part.getContentType() + "|" + part.getContent());
        }
    }

    @Test
    void aSendFailureNeverPropagatesAndNeverLeaksSecrets() {
        Mockito.doThrow(new MailSendException("535 auth failed password=hunter2 for " + ADDRESS + " " + SECRET_TOKEN))
                .when(javaMailSender).send(any(MimeMessage.class));

        assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();

        assertThat(logs.list).isNotEmpty();
        var all = new StringBuilder();
        logs.list.forEach(e -> {
            all.append(e.getFormattedMessage()).append('\n');
            assertThat(e.getThrowableProxy()).as("no throwable attached to the log event").isNull();
        });
        assertThat(all.toString())
                .contains("EMAIL_VERIFICATION")
                .contains("42")
                .contains("MailSendException")
                .doesNotContain(SECRET_TOKEN)
                .doesNotContain(ADDRESS)
                .doesNotContain("hunter2")
                .doesNotContain("auth failed");
    }

    @Test
    void anInvalidSenderOrRecipientAddressIsSwallowedToo() {
        var bad = new EmailMessage(EmailType.PASSWORD_RESET, 5, "not an address <<", "s", "t", "<p>h</p>");
        assertThatCode(() -> sender.send(bad)).doesNotThrowAnyException();
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat))
                .doesNotContain("not an address");
    }

    @Test
    void successLogsTypeAndUserIdOnly() {
        sender.send(message);
        var all = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertThat(all).contains("EMAIL_VERIFICATION").contains("42")
                .doesNotContain(SECRET_TOKEN).doesNotContain(ADDRESS);
    }
}
