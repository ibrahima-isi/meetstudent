package com.bowe.meetstudent.unit.mail;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bowe.meetstudent.mail.DevLogEmailSender;
import com.bowe.meetstudent.mail.DisabledEmailSender;
import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.SendOutcome;
import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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

    @Test
    void setsReplyToWhenTheMessageHasOne() throws Exception {
        sender.send(new EmailMessage(EmailType.CONTACT_MESSAGE, null, "inbox@example.com", "[Contact] s", "t", "<p>h</p>", "awa@example.org"));

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        assertThat(((InternetAddress) captor.getValue().getReplyTo()[0]).getAddress()).isEqualTo("awa@example.org");
    }

    @Test
    void anInvalidReplyToSendsNothingRatherThanASpoofedHeader() {
        sender.send(new EmailMessage(EmailType.CONTACT_MESSAGE, null, "inbox@example.com", "s", "t", "<p>h</p>", "a@example.org, b@example.org"));
        Mockito.verify(javaMailSender, Mockito.never()).send(any(MimeMessage.class));
    }

    @Test
    void aMessageWithoutReplyToHasNone() throws Exception {
        sender.send(message);
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        // MimeMessage.getReplyTo() falls back to From when the header is absent.
        assertThat(captor.getValue().getHeader("Reply-To")).isNull();
    }

    @Test
    void multipleOrGroupRecipientsAreRefusedAndNothingIsSent() {
        for (String to : new String[]{"a@example.com, b@example.com", "Team: a@example.com, b@example.com;", "a@@example.com", "a b@example.com"}) {
            sender.send(new EmailMessage(EmailType.PASSWORD_RESET, 5, to, "s", "t", "<p>h</p>"));
        }
        Mockito.verify(javaMailSender, Mockito.never()).send(any(MimeMessage.class));
    }

    // ---- delivery outcome ----------------------------------------------------------------

    private SendOutcome failWith(Exception failure) {
        Mockito.doThrow(failure instanceof RuntimeException r ? r : new MailSendException("wrapped", failure))
                .when(javaMailSender).send(any(MimeMessage.class));
        return sender.send(message);
    }

    @Test
    void aSuccessfulSendReportsSent() {
        assertThat(sender.send(message)).isEqualTo(SendOutcome.SENT);
    }

    @Test
    void authenticationFailuresAreAuthFailed() {
        assertThat(failWith(new MailAuthenticationException("535 bad credentials"))).isEqualTo(SendOutcome.AUTH_FAILED);
        assertThat(failWith(new MailSendException("x", new AuthenticationFailedException("535")))).isEqualTo(SendOutcome.AUTH_FAILED);
    }

    @Test
    void connectionProblemsAreConnectionFailedWhateverTheWrapping() {
        assertThat(failWith(new MailSendException("down", new MessagingException("Could not connect", new ConnectException("refused")))))
                .isEqualTo(SendOutcome.CONNECTION_FAILED);
        assertThat(failWith(new MailSendException("slow", new MessagingException("x", new SocketTimeoutException("Read timed out")))))
                .isEqualTo(SendOutcome.CONNECTION_FAILED);
        assertThat(failWith(new MailSendException("dns", new MessagingException("x", new UnknownHostException("smtp.nowhere")))))
                .isEqualTo(SendOutcome.CONNECTION_FAILED);
        assertThat(failWith(new MailSendException("dns", new MessagingException("x", new java.net.NoRouteToHostException()))))
                .isEqualTo(SendOutcome.CONNECTION_FAILED);
    }

    @Test
    void tlsProblemsAreTlsFailed() {
        assertThat(failWith(new MailSendException("tls", new MessagingException("Could not convert socket to TLS", new SSLHandshakeException("PKIX path")))))
                .isEqualTo(SendOutcome.TLS_FAILED);
        assertThat(failWith(new MailSendException("tls", new SSLException("Unsupported or unrecognized SSL message"))))
                .isEqualTo(SendOutcome.TLS_FAILED);
        assertThat(failWith(new MailSendException("tls", new MessagingException("STARTTLS is required but host does not support STARTTLS"))))
                .isEqualTo(SendOutcome.TLS_FAILED);
    }

    @Test
    void recipientRefusalsAreRejected() throws Exception {
        assertThat(failWith(new MailSendException("refused", new SendFailedException("Invalid Addresses", null, new Address[0], new Address[0],
                new Address[]{new InternetAddress("nobody@example.com")})))).isEqualTo(SendOutcome.REJECTED);
        assertThat(failWith(new MailSendException("refused", new SMTPSendFailedException("RCPT TO", 550, "550 no such user", null, null, null, null))))
                .isEqualTo(SendOutcome.REJECTED);
        assertThat(failWith(new MailSendException("refused", new AddressException("bad")))).isEqualTo(SendOutcome.REJECTED);
    }

    @Test
    void aFailureSpringKeepsOnlyInItsFailedMessagesMapIsStillClassified() {
        var failure = new MailSendException(java.util.Map.of("m", new MessagingException("x", new ConnectException("refused"))));
        assertThat(failWith(failure)).isEqualTo(SendOutcome.CONNECTION_FAILED);
    }

    @Test
    void aTemporarySmtpRefusalAndUnknownErrorsAreFailedNotRejected() {
        assertThat(failWith(new IllegalStateException("surprise"))).isEqualTo(SendOutcome.FAILED);
        assertThat(failWith(new MailSendException("odd"))).isEqualTo(SendOutcome.FAILED);
    }

    /** What Angus Mail really throws for "451 try later" on RCPT TO (probed against a fake SMTP server): validUnsent=1, invalid=0. */
    @Test
    void aTemporaryRefusalOnRcptIsFailedSoItIsRetried() throws Exception {
        var to = new InternetAddress("d@e.f");
        var cause = new SMTPAddressFailedException(to, "RCPT TO:<d@e.f>", 451, "451 try later");
        var real = new SendFailedException("Invalid Addresses", cause, new Address[0], new Address[]{to}, new Address[0]);
        assertThat(failWith(new MailSendException("busy", real))).isEqualTo(SendOutcome.FAILED);
        assertThat(SendOutcome.FAILED.isTransient()).isTrue();
    }

    /** "421 rate limited" on DATA: SMTPSendFailedException code=421 with validUnsent=1. */
    @Test
    void aTemporaryRefusalOnDataIsFailedSoItIsRetried() throws Exception {
        var to = new InternetAddress("d@e.f");
        var real = new SMTPSendFailedException("DATA", 421, "421 rate limited", null, new Address[0], new Address[]{to}, new Address[0]);
        assertThat(failWith(new MailSendException("busy", real))).isEqualTo(SendOutcome.FAILED);
    }

    @Test
    void aPermanentRefusalOnRcptIsRejected() throws Exception {
        var to = new InternetAddress("d@e.f");
        var cause = new SMTPAddressFailedException(to, "RCPT TO:<d@e.f>", 550, "550 no such user");
        var real = new SendFailedException("Invalid Addresses", cause, new Address[0], new Address[0], new Address[]{to});
        assertThat(failWith(new MailSendException("refused", real))).isEqualTo(SendOutcome.REJECTED);
        // A 5xx wins even when the exception only lists the recipient as valid-but-unsent.
        var odd = new SMTPSendFailedException("DATA", 554, "554 rejected", null, new Address[0], new Address[]{to}, new Address[0]);
        assertThat(failWith(new MailSendException("refused", odd))).isEqualTo(SendOutcome.REJECTED);
    }

    @Test
    void aSendFailureWithoutAnyCodeIsNotPermanent() throws Exception {
        var to = new InternetAddress("d@e.f");
        var real = new SendFailedException("odd", null, new Address[0], new Address[]{to}, new Address[0]);
        assertThat(failWith(new MailSendException("x", real))).isEqualTo(SendOutcome.FAILED);
    }

    @Test
    void anAddressRefusedBeforeSendingIsRejectedAndNothingIsSent() {
        var bad = new EmailMessage(EmailType.PASSWORD_RESET, 5, "a@@example.com", "s", "t", "<p>h</p>");
        assertThat(sender.send(bad)).isEqualTo(SendOutcome.REJECTED);
        Mockito.verify(javaMailSender, Mockito.never()).send(any(MimeMessage.class));
    }

    @Test
    void theOutcomeIsLoggedButNothingSensitiveIs() {
        failWith(new MailAuthenticationException("535 password=hunter2 " + ADDRESS + " " + SECRET_TOKEN));
        var all = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertThat(all).contains("AUTH_FAILED").doesNotContain("hunter2").doesNotContain(ADDRESS).doesNotContain(SECRET_TOKEN);
    }

    @Test
    void disabledAndDevLogSendersReportTheirOutcome() {
        assertThat(new DisabledEmailSender().send(message)).isEqualTo(SendOutcome.DISABLED);
        assertThat(new DevLogEmailSender().send(message)).isEqualTo(SendOutcome.SENT);
        assertThat(SendOutcome.AUTH_FAILED.isTransient()).isFalse();
        assertThat(SendOutcome.REJECTED.isTransient()).isFalse();
        assertThat(SendOutcome.DISABLED.isTransient()).isFalse();
        assertThat(SendOutcome.SENT.isTransient()).isFalse();
        assertThat(SendOutcome.CONNECTION_FAILED.isTransient()).isTrue();
        assertThat(SendOutcome.TLS_FAILED.isTransient()).isTrue();
        assertThat(SendOutcome.FAILED.isTransient()).isTrue();
    }
}
