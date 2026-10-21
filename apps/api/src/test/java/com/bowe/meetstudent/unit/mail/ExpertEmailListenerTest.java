package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.ExpertEmailListener;
import com.bowe.meetstudent.mail.ExpertEmailRequested;
import com.bowe.meetstudent.mail.SendOutcome;
import com.bowe.meetstudent.support.CapturingEmailSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExpertEmailListenerTest {

    @Test
    void aThrowingSenderNeverPropagates() {
        EmailSender boom = new EmailSender() {
            @Override
            public SendOutcome send(EmailMessage message) {
                throw new IllegalStateException("smtp exploded for a@example.com");
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
        assertThatCode(() -> new ExpertEmailListener(boom).onExpertEmailRequested(
                new ExpertEmailRequested(EmailType.EXPERT_APPROVED, 3, "a@example.com", "Awa", "fr", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void sendsTheRenderedMailToTheEventAddress() {
        var capture = new CapturingEmailSender();
        new ExpertEmailListener(capture).onExpertEmailRequested(
                new ExpertEmailRequested(EmailType.EXPERT_REQUEST_RECEIVED, 3, "a@example.com", "Awa", "en", null));
        assertThat(capture.last().to()).isEqualTo("a@example.com");
        assertThat(capture.last().type()).isEqualTo(EmailType.EXPERT_REQUEST_RECEIVED);
    }

    @Test
    void theEventNeverPrintsAddressNameOrReason() {
        var event = new ExpertEmailRequested(EmailType.EXPERT_REJECTED, 3, "a@example.com", "Awa", "fr", "secret motif");
        assertThat(event.toString()).doesNotContain("a@example.com").doesNotContain("Awa").doesNotContain("secret");
    }
}
