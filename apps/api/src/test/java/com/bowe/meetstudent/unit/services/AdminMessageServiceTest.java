package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.config.ContactProperties;
import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.exceptions.ServiceUnavailableException;
import com.bowe.meetstudent.mail.ContactMessageReceived;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import com.bowe.meetstudent.services.AdminMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMessageServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    private final ContactMessageRepository repository = mock(ContactMessageRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ContactProperties properties = new ContactProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AdminMessageService service = new AdminMessageService(repository, emailSender, events, properties, clock);

    private ContactMessage failed() {
        return ContactMessage.builder().id(9).senderEmail("awa@example.com").subject("S").body("B").language("fr")
                .createdAt(Instant.parse("2026-03-01T10:00:00Z")).mailStatus(ContactMailStatus.FAILED).mailAttempts(3)
                .mailSentAt(Instant.parse("2026-03-01T10:00:09Z")).build();
    }

    @Test
    void resendClaimsTheMessageAtomicallyThenPublishesTheEvent() {
        when(repository.findById(9)).thenReturn(Optional.of(failed()));
        when(emailSender.isEnabled()).thenReturn(true);
        when(repository.claimResend(9, NOW, NOW.minus(Duration.ofMinutes(15)))).thenReturn(1);

        service.resend(9);

        // Never a read-modify-write of the entity: a stale copy could overwrite a newer delivery state.
        verify(repository, never()).save(any());
        var event = ArgumentCaptor.forClass(ContactMessageReceived.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().messageId()).isEqualTo(9);
        assertThat(event.getValue().senderEmail()).isEqualTo("awa@example.com");
        assertThat(event.getValue().message()).isEqualTo("B");
        assertThat(event.getValue().lang()).isEqualTo("fr");
    }

    @Test
    void aPendingMessageThatIsNotStaleIsAConflictAndQueuesNothing() {
        when(repository.findById(9)).thenReturn(Optional.of(failed()));
        when(emailSender.isEnabled()).thenReturn(true);
        when(repository.claimResend(anyInt(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resend(9)).isInstanceOf(ConflictException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void thePendingTimeoutIsConfigurableAndABlankOrNonPositiveOneFallsBackToFifteenMinutes() {
        when(repository.findById(9)).thenReturn(Optional.of(failed()));
        when(emailSender.isEnabled()).thenReturn(true);
        when(repository.claimResend(anyInt(), any(), any())).thenReturn(1);

        properties.getMail().setPendingTimeout(Duration.ofMinutes(2));
        service.resend(9);
        verify(repository).claimResend(9, NOW, NOW.minus(Duration.ofMinutes(2)));

        properties.getMail().setPendingTimeout(null);
        service.resend(9);
        properties.getMail().setPendingTimeout(Duration.ZERO);
        service.resend(9);
        verify(repository, times(2)).claimResend(9, NOW, NOW.minus(Duration.ofMinutes(15)));
    }

    @Test
    void resendOfAnUnknownMessageIsNotFoundEvenWhenMailIsDisabled() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(emailSender.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.resend(1)).isInstanceOf(ResourceNotFoundException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void resendWhileMailIsDisabledIsUnavailableBeforeAnyConflictCheckAndTouchesNothing() {
        when(repository.findById(9)).thenReturn(Optional.of(failed()));
        when(emailSender.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.resend(9)).isInstanceOf(ServiceUnavailableException.class);

        verify(repository, never()).claimResend(anyInt(), any(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
