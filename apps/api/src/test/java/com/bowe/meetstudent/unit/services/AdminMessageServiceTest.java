package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.exceptions.ServiceUnavailableException;
import com.bowe.meetstudent.mail.ContactMessageReceived;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import com.bowe.meetstudent.services.AdminMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMessageServiceTest {

    private final ContactMessageRepository repository = mock(ContactMessageRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AdminMessageService service = new AdminMessageService(repository, emailSender, events);

    private ContactMessage failed() {
        return ContactMessage.builder().id(9).senderEmail("awa@example.com").subject("S").body("B").language("fr")
                .createdAt(Instant.parse("2026-03-01T10:00:00Z")).mailStatus(ContactMailStatus.FAILED).mailAttempts(3)
                .mailSentAt(Instant.parse("2026-03-01T10:00:09Z")).build();
    }

    @Test
    void resendResetsTheDeliveryStateToPendingAndPublishesTheEvent() {
        var message = failed();
        when(repository.findById(9)).thenReturn(Optional.of(message));
        when(emailSender.isEnabled()).thenReturn(true);

        service.resend(9);

        assertThat(message.getMailStatus()).isEqualTo(ContactMailStatus.PENDING);
        assertThat(message.getMailAttempts()).isZero();
        assertThat(message.getMailSentAt()).isNull();
        verify(repository).save(message);
        var event = ArgumentCaptor.forClass(ContactMessageReceived.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().messageId()).isEqualTo(9);
        assertThat(event.getValue().senderEmail()).isEqualTo("awa@example.com");
        assertThat(event.getValue().message()).isEqualTo("B");
        assertThat(event.getValue().lang()).isEqualTo("fr");
    }

    @Test
    void resendOfAnUnknownMessageIsNotFound() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(emailSender.isEnabled()).thenReturn(true);
        assertThatThrownBy(() -> service.resend(1)).isInstanceOf(ResourceNotFoundException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void resendWhileMailIsDisabledIsUnavailableAndTouchesNothing() {
        var message = failed();
        when(repository.findById(9)).thenReturn(Optional.of(message));
        when(emailSender.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.resend(9)).isInstanceOf(ServiceUnavailableException.class);

        assertThat(message.getMailStatus()).isEqualTo(ContactMailStatus.FAILED);
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
