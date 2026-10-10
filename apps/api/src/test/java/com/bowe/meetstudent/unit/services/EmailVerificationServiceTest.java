package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.UserToken;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.exceptions.ServiceUnavailableException;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.mail.AccountEmailRequested;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.EmailVerificationService;
import com.bowe.meetstudent.services.UserTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTokenService userTokenService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private ApplicationEventPublisher events;

    private EmailVerificationService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, userTokenService, emailSender, new MailProperties(), events);
        user = UserEntity.builder().email("jane@example.com").firstname("Jane").build();
        user.setId(7);
    }

    private AccountEmailRequested publishedEvent() {
        var captor = ArgumentCaptor.forClass(AccountEmailRequested.class);
        verify(events, times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendVerificationPublishesOneEventWithFrenchByDefault() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(eq(user), eq(UserTokenPurpose.EMAIL_VERIFICATION), eq(Duration.ofHours(24)))).thenReturn("raw-token");

        service.sendVerification(user, null);

        var event = publishedEvent();
        assertEquals(EmailType.EMAIL_VERIFICATION, event.type());
        assertEquals("fr", event.lang());
        assertEquals("jane@example.com", event.to());
        assertEquals("Jane", event.firstName());
        assertEquals(7, event.userId());
        assertEquals("raw-token", event.token());
    }

    @Test
    void sendVerificationKeepsEnglishAndFallsBackToFrenchForAnythingElse() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenReturn("t");

        service.sendVerification(user, "EN");
        service.sendVerification(user, "de");

        var captor = ArgumentCaptor.forClass(AccountEmailRequested.class);
        verify(events, times(2)).publishEvent(captor.capture());
        assertEquals("en", captor.getAllValues().get(0).lang());
        assertEquals("fr", captor.getAllValues().get(1).lang());
    }

    @Test
    void sendVerificationDoesNothingWhileMailIsDisabled() {
        when(emailSender.isEnabled()).thenReturn(false);

        service.sendVerification(user, "fr");

        verifyNoInteractions(userTokenService);
        verifyNoInteractions(events);
    }

    @Test
    void confirmMarksTheTokenOwnerVerified() {
        var token = UserToken.builder().user(user).build();
        when(userTokenService.consume("raw", UserTokenPurpose.EMAIL_VERIFICATION)).thenReturn(token);

        service.confirm("raw");

        assertTrue(user.isEmailVerified());
        verify(userRepository).save(user);
    }

    @Test
    void confirmIsIdempotentForAnAlreadyVerifiedUser() {
        user.setEmailVerified(true);
        when(userTokenService.consume("raw", UserTokenPurpose.EMAIL_VERIFICATION)).thenReturn(UserToken.builder().user(user).build());

        service.confirm("raw");

        assertTrue(user.isEmailVerified());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendIssuesANewTokenForAnUnverifiedUser() {
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenReturn("t");

        service.resend(7, "en");

        assertEquals("en", publishedEvent().lang());
    }

    @Test
    void resendConflictsWhenTheUserIsAlreadyVerified() {
        user.setEmailVerified(true);
        when(userRepository.findById(7)).thenReturn(Optional.of(user));

        assertThrows(ConflictException.class, () -> service.resend(7, "fr"));
        verifyNoInteractions(userTokenService);
    }

    @Test
    void resendIs503WhenMailIsDisabled() {
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(emailSender.isEnabled()).thenReturn(false);

        assertThrows(ServiceUnavailableException.class, () -> service.resend(7, "fr"));
        verifyNoInteractions(userTokenService);
    }

    @Test
    void resendOfAnUnknownUserIsNotFound() {
        when(userRepository.findById(9)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.resend(9, "fr"));
    }

    @Test
    void resendPropagatesTheCooldown() {
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenThrow(new TooManyRequestsException("slow down", 42));

        var ex = assertThrows(TooManyRequestsException.class, () -> service.resend(7, "fr"));
        assertEquals(42, ex.getRetryAfterSeconds());
    }

    @Test
    void restartVerificationRevokesOldTokensAndSendsANewOne() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenReturn("t");

        service.restartVerification(user);

        verify(userTokenService).consumeOutstanding(7, UserTokenPurpose.EMAIL_VERIFICATION);
        publishedEvent();
    }

    @Test
    void restartVerificationRevokesOldTokensEvenWhenMailIsDisabledAndSwallowsTheCooldown() {
        when(emailSender.isEnabled()).thenReturn(false);
        service.restartVerification(user);
        verify(userTokenService).consumeOutstanding(7, UserTokenPurpose.EMAIL_VERIFICATION);
        verifyNoInteractions(events);
    }

    @Test
    void restartVerificationDoesNotFailWhenTheCooldownApplies() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenThrow(new TooManyRequestsException("slow down", 5));

        assertDoesNotThrow(() -> service.restartVerification(user));
        verify(events, never()).publishEvent(any(Object.class));
    }
}
