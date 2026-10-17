package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.config.ContactProperties;
import com.bowe.meetstudent.mail.ContactEmailListener;
import com.bowe.meetstudent.mail.ContactMessageReceived;
import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.support.CapturingEmailSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ContactEmailListenerTest {

    private final ContactProperties properties = new ContactProperties();
    private final CapturingEmailSender mailbox = new CapturingEmailSender();
    private final ContactEmailListener listener = new ContactEmailListener(mailbox, properties);

    private static ContactMessageReceived event(String email, String subject, String message) {
        return new ContactMessageReceived(7, email, subject, message, "fr");
    }

    @Test
    void sendsToTheDefaultRecipientWithAnOriginTaggedSubjectAndReplyTo() {
        listener.onContactMessageReceived(event("awa@example.com", "Question", "Bonjour,\nune question."));

        var mail = mailbox.last();
        assertThat(mail.type()).isEqualTo(EmailType.CONTACT_MESSAGE);
        assertThat(mail.to()).isEqualTo("contact@meetstudent.com");
        assertThat(mail.subject()).isEqualTo("[Contact] Question");
        assertThat(mail.replyTo()).isEqualTo("awa@example.com");
        assertThat(mail.text()).contains("awa@example.com").contains("Bonjour,\nune question.");
        assertThat(mail.html()).contains("awa@example.com");
    }

    @Test
    void usesTheConfiguredRecipientAndFallsBackWhenItIsBlank() {
        properties.setRecipient("team@example.org");
        listener.onContactMessageReceived(event("awa@example.com", "s", "m"));
        assertThat(mailbox.last().to()).isEqualTo("team@example.org");

        properties.setRecipient("  ");
        listener.onContactMessageReceived(event("awa@example.com", "s", "m"));
        assertThat(mailbox.last().to()).isEqualTo("contact@meetstudent.com");
    }

    @Test
    void lineBreaksNeverReachTheSubject() {
        listener.onContactMessageReceived(event("awa@example.com", "Hi\r\nBcc: victim@example.com", "m"));

        var mail = mailbox.last();
        assertThat(mail).isNotNull();
        assertThat(mail.subject()).doesNotContain("\r").doesNotContain("\n").startsWith("[Contact] Hi");
    }

    @Test
    void aSenderAddressCarryingALineBreakIsNeverUsedAsReplyTo() {
        listener.onContactMessageReceived(event("awa@example.com\r\nBcc: victim@example.com", "s", "m"));

        var mail = mailbox.last();
        assertThat(mail).isNotNull();
        assertThat(mail.replyTo()).isNull();
    }

    @Test
    void htmlEscapesTheContent() {
        listener.onContactMessageReceived(event("awa@example.com", "<b>s</b>", "<script>alert(1)</script>"));
        assertThat(mailbox.last().html()).doesNotContain("<script>").doesNotContain("<b>s</b>").contains("&lt;script&gt;");
    }

    @Test
    void aFailingSenderNeverPropagates() {
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
        assertThatCode(() -> new ContactEmailListener(boom, properties)
                .onContactMessageReceived(event("awa@example.com", "s", "m"))).doesNotThrowAnyException();
    }

    @Test
    void theEventNeverPrintsTheAddressOrTheBody() {
        var text = event("secret@example.com", "subj", "private body").toString();
        assertThat(text).doesNotContain("secret@example.com").doesNotContain("private body");
    }
}
