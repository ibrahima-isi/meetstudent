package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.config.ContactProperties;
import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import com.bowe.meetstudent.mail.ContactEmailListener;
import com.bowe.meetstudent.mail.ContactMailStatusRecorder;
import com.bowe.meetstudent.mail.SendOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
    private final RecordingRecorder recorder = new RecordingRecorder();
    private final List<Duration> slept = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);
    private final ContactEmailListener listener = new ContactEmailListener(mailbox, properties, recorder, clock, slept::add);

    /** Collects what the listener persists. */
    private static final class RecordingRecorder implements ContactMailStatusRecorder {
        record Entry(Integer id, ContactMailStatus status, int attempts, Instant sentAt) {
        }

        final List<Entry> entries = new ArrayList<>();

        @Override
        public void record(Integer messageId, ContactMailStatus status, int attempts, Instant sentAt) {
            entries.add(new Entry(messageId, status, attempts, sentAt));
        }

        Entry only() {
            assertThat(entries).hasSize(1);
            return entries.get(0);
        }
    }

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
    void aNonAsciiSenderIsNotUsedAsReplyToButStaysInTheBody() {
        listener.onContactMessageReceived(event("été@example.com", "s", "m"));
        var mail = mailbox.last();
        assertThat(mail.replyTo()).isNull();
        assertThat(mail.text()).contains("été@example.com");

        listener.onContactMessageReceived(event("awa@exämple.com", "s", "m"));
        assertThat(mailbox.last().replyTo()).isNull();

        listener.onContactMessageReceived(event("awa@example.com", "s", "m"));
        assertThat(mailbox.last().replyTo()).isEqualTo("awa@example.com");
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
            public SendOutcome send(EmailMessage message) {
                throw new IllegalStateException("smtp exploded");
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
        assertThatCode(() -> new ContactEmailListener(boom, properties, recorder, clock, slept::add)
                .onContactMessageReceived(event("awa@example.com", "s", "m"))).doesNotThrowAnyException();
        assertThat(recorder.only().status()).isEqualTo(ContactMailStatus.FAILED);
    }

    @Test
    void theEventNeverPrintsTheAddressOrTheBody() {
        var text = event("secret@example.com", "subj", "private body").toString();
        assertThat(text).doesNotContain("secret@example.com").doesNotContain("private body");
    }

    // ---- delivery: retry and persisted result ----------------------------------------------

    /** Sender that answers the scripted outcomes in order, then the last one for ever. */
    private static EmailSender scripted(List<EmailMessage> seen, SendOutcome... outcomes) {
        var calls = new int[1];
        return new EmailSender() {
            @Override
            public SendOutcome send(EmailMessage message) {
                seen.add(message);
                return outcomes[Math.min(calls[0]++, outcomes.length - 1)];
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
    }

    private ContactEmailListener with(EmailSender sender) {
        return new ContactEmailListener(sender, properties, recorder, clock, slept::add);
    }

    @Test
    void aFirstTrySuccessIsRecordedAsSentWithItsTimeAndNeverSleeps() {
        listener.onContactMessageReceived(event("awa@example.com", "s", "m"));

        var entry = recorder.only();
        assertThat(entry.id()).isEqualTo(7);
        assertThat(entry.status()).isEqualTo(ContactMailStatus.SENT);
        assertThat(entry.attempts()).isEqualTo(1);
        assertThat(entry.sentAt()).isEqualTo(Instant.parse("2026-10-18T10:00:00Z"));
        assertThat(slept).isEmpty();
    }

    @Test
    void aMailThatWasOnlyLoggedIsSkippedNeverSent() {
        var seen = new ArrayList<EmailMessage>();
        with(scripted(seen, SendOutcome.LOGGED)).onContactMessageReceived(event("awa@example.com", "s", "m"));

        assertThat(seen).hasSize(1);
        var entry = recorder.only();
        assertThat(entry.status()).isEqualTo(ContactMailStatus.SKIPPED);
        assertThat(entry.attempts()).isZero();
        assertThat(entry.sentAt()).isNull();
        assertThat(slept).isEmpty();
    }

    @Test
    void transientFailuresAreRetriedWithBackoffUntilItWorks() {
        var seen = new ArrayList<EmailMessage>();
        with(scripted(seen, SendOutcome.CONNECTION_FAILED, SendOutcome.TLS_FAILED, SendOutcome.SENT))
                .onContactMessageReceived(event("awa@example.com", "s", "m"));

        assertThat(seen).hasSize(3);
        assertThat(slept).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(10));
        var entry = recorder.only();
        assertThat(entry.status()).isEqualTo(ContactMailStatus.SENT);
        assertThat(entry.attempts()).isEqualTo(3);
    }

    @Test
    void afterTheLastAttemptTheMessageIsFailedAndNoFurtherWaitHappens() {
        var seen = new ArrayList<EmailMessage>();
        with(scripted(seen, SendOutcome.FAILED)).onContactMessageReceived(event("awa@example.com", "s", "m"));

        assertThat(seen).hasSize(3);
        assertThat(slept).hasSize(2);
        var entry = recorder.only();
        assertThat(entry.status()).isEqualTo(ContactMailStatus.FAILED);
        assertThat(entry.attempts()).isEqualTo(3);
        assertThat(entry.sentAt()).isNull();
    }

    @Test
    void permanentFailuresAreNeverRetried() {
        for (var outcome : new SendOutcome[]{SendOutcome.AUTH_FAILED, SendOutcome.REJECTED}) {
            var seen = new ArrayList<EmailMessage>();
            recorder.entries.clear();
            with(scripted(seen, outcome)).onContactMessageReceived(event("awa@example.com", "s", "m"));

            assertThat(seen).as(outcome.name()).hasSize(1);
            assertThat(recorder.only().status()).isEqualTo(ContactMailStatus.FAILED);
            assertThat(recorder.only().attempts()).isEqualTo(1);
        }
        assertThat(slept).isEmpty();
    }

    @Test
    void disabledMailIsSkippedWithoutAttemptsOrRetry() {
        var seen = new ArrayList<EmailMessage>();
        with(scripted(seen, SendOutcome.DISABLED)).onContactMessageReceived(event("awa@example.com", "s", "m"));

        assertThat(seen).hasSize(1);
        assertThat(slept).isEmpty();
        var entry = recorder.only();
        assertThat(entry.status()).isEqualTo(ContactMailStatus.SKIPPED);
        assertThat(entry.attempts()).isZero();
    }

    @Test
    void anExceptionFromTheSenderCountsAsAFailedAttemptAndIsRetried() {
        var calls = new int[1];
        EmailSender boom = new EmailSender() {
            @Override
            public SendOutcome send(EmailMessage message) {
                calls[0]++;
                throw new IllegalStateException("smtp exploded");
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
        with(boom).onContactMessageReceived(event("awa@example.com", "s", "m"));

        assertThat(calls[0]).isEqualTo(3);
        assertThat(recorder.only().status()).isEqualTo(ContactMailStatus.FAILED);
    }

    @Test
    void theRetrySettingsAreHonouredAndBounded() {
        properties.getMail().getRetry().setMaxAttempts(2);
        properties.getMail().getRetry().setInitialDelay(Duration.ofMillis(500));
        var seen = new ArrayList<EmailMessage>();
        with(scripted(seen, SendOutcome.CONNECTION_FAILED)).onContactMessageReceived(event("awa@example.com", "s", "m"));
        assertThat(seen).hasSize(2);
        assertThat(slept).containsExactly(Duration.ofMillis(500));

        properties.getMail().getRetry().setMaxAttempts(1);
        seen.clear();
        with(scripted(seen, SendOutcome.CONNECTION_FAILED)).onContactMessageReceived(event("awa@example.com", "s", "m"));
        assertThat(seen).hasSize(1);

        properties.getMail().getRetry().setMaxAttempts(1000);
        seen.clear();
        with(scripted(seen, SendOutcome.CONNECTION_FAILED)).onContactMessageReceived(event("awa@example.com", "s", "m"));
        assertThat(seen).hasSize(ContactProperties.Mail.MAX_ATTEMPTS_LIMIT);
        assertThat(slept).allSatisfy(d -> assertThat(d).isLessThanOrEqualTo(ContactProperties.Mail.MAX_DELAY));
    }

    @Test
    void anInterruptedWaitStopsTheRetriesAndKeepsTheInterruptFlag() {
        var seen = new ArrayList<EmailMessage>();
        var listener = new ContactEmailListener(scripted(seen, SendOutcome.CONNECTION_FAILED), properties, recorder, clock, d -> {
            throw new InterruptedException();
        });
        try {
            listener.onContactMessageReceived(event("awa@example.com", "s", "m"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(seen).hasSize(1);
        assertThat(recorder.only().status()).isEqualTo(ContactMailStatus.FAILED);
    }

    @Test
    void aFailingRecorderNeverPropagates() {
        ContactMailStatusRecorder broken = (id, status, attempts, sentAt) -> {
            throw new IllegalStateException("db down");
        };
        assertThatCode(() -> new ContactEmailListener(mailbox, properties, broken, clock, slept::add)
                .onContactMessageReceived(event("awa@example.com", "s", "m"))).doesNotThrowAnyException();
    }

    @Test
    void theSameMessageObjectIsResentOnEachAttemptAndTheRecipientIsNeverLogged() {
        var seen = new ArrayList<EmailMessage>();
        with(scripted(seen, SendOutcome.CONNECTION_FAILED, SendOutcome.SENT)).onContactMessageReceived(event("awa@example.com", "s", "m"));
        assertThat(seen).hasSize(2);
        assertThat(seen.get(0)).isSameAs(seen.get(1));
    }
}
