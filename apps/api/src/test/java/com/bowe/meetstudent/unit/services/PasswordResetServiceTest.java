package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.UserToken;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.InvalidActionTokenException;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.mail.AccountEmailRequested;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.PasswordResetService;
import com.bowe.meetstudent.services.RefreshTokenService;
import com.bowe.meetstudent.services.UserTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserTokenService userTokenService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private EmailSender emailSender;
    @Mock private ApplicationEventPublisher events;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private PasswordResetService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, userTokenService, refreshTokenService, encoder,
                emailSender, new MailProperties(), events);
        user = UserEntity.builder().email("jane@example.com").firstname("Jane").password(encoder.encode("old-password")).build();
        user.setId(7);
    }

    // ---- request ----

    @Test
    void unknownEmailCreatesNoTokenNoEventAndDoesNotThrow() {
        when(userRepository.findAllByEmailIgnoreCaseOrderByIdAsc("nobody@example.com")).thenReturn(List.of());

        assertDoesNotThrow(() -> service.request("nobody@example.com", "fr"));

        verify(userRepository).findAllByEmailIgnoreCaseOrderByIdAsc("nobody@example.com");
        verifyNoInteractions(userTokenService);
        verifyNoInteractions(events);
    }

    @Test
    void knownEmailIssuesAOneHourResetTokenAndPublishesOneEvent() {
        when(userRepository.findAllByEmailIgnoreCaseOrderByIdAsc("jane@example.com")).thenReturn(List.of(user));
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(eq(user), eq(UserTokenPurpose.PASSWORD_RESET), eq(Duration.ofHours(1)))).thenReturn("raw-token");

        service.request("jane@example.com", "en");

        var captor = ArgumentCaptor.forClass(AccountEmailRequested.class);
        verify(events, times(1)).publishEvent(captor.capture());
        var event = captor.getValue();
        assertEquals(EmailType.PASSWORD_RESET, event.type());
        assertEquals("en", event.lang());
        assertEquals("jane@example.com", event.to());
        assertEquals("Jane", event.firstName());
        assertEquals(7, event.userId());
        assertEquals("raw-token", event.token());
    }

    @Test
    void unsupportedLanguageFallsBackToFrench() {
        when(userRepository.findAllByEmailIgnoreCaseOrderByIdAsc(anyString())).thenReturn(List.of(user));
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenReturn("t");

        service.request("jane@example.com", "de");

        var captor = ArgumentCaptor.forClass(AccountEmailRequested.class);
        verify(events).publishEvent(captor.capture());
        assertEquals("fr", captor.getValue().lang());
    }

    @Test
    void cooldownOrCapSilentlyProducesNoEventAndNoException() {
        when(userRepository.findAllByEmailIgnoreCaseOrderByIdAsc(anyString())).thenReturn(List.of(user));
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenThrow(new TooManyRequestsException("wait", 30));

        assertDoesNotThrow(() -> service.request("jane@example.com", "fr"));

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void mailDisabledIssuesNothingAndDoesNotThrow() {
        when(userRepository.findAllByEmailIgnoreCaseOrderByIdAsc(anyString())).thenReturn(List.of(user));
        when(emailSender.isEnabled()).thenReturn(false);

        assertDoesNotThrow(() -> service.request("jane@example.com", "fr"));

        verifyNoInteractions(userTokenService);
        verifyNoInteractions(events);
    }

    @Test
    void caseVariantDuplicatesDoNotThrowAndTheOldestAccountIsUsed() {
        var twin = UserEntity.builder().email("JANE@example.com").firstname("Twin").build();
        twin.setId(99);
        when(userRepository.findAllByEmailIgnoreCaseOrderByIdAsc("jane@example.com")).thenReturn(List.of(user, twin));
        when(emailSender.isEnabled()).thenReturn(true);
        when(userTokenService.issue(any(), any(), any())).thenReturn("t");

        assertDoesNotThrow(() -> service.request("jane@example.com", "fr"));

        verify(userTokenService, times(1)).issue(eq(user), eq(UserTokenPurpose.PASSWORD_RESET), any());
        var captor = ArgumentCaptor.forClass(AccountEmailRequested.class);
        verify(events, times(1)).publishEvent(captor.capture());
        assertEquals(7, captor.getValue().userId());
    }

    // ---- confirm ----

    private UserToken tokenFor(UserEntity owner) {
        return UserToken.builder().user(owner).email(owner.getEmail()).purpose(UserTokenPurpose.PASSWORD_RESET).build();
    }

    @Test
    void confirmStoresTheHashOfTheNewPasswordWithATargetedUpdate() {
        when(userTokenService.consume("raw", UserTokenPurpose.PASSWORD_RESET)).thenReturn(tokenFor(user));

        service.confirm("raw", "brand-new-pass", "brand-new-pass");

        var stored = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(eq(7), stored.capture());
        assertTrue(encoder.matches("brand-new-pass", stored.getValue()));
        assertFalse(encoder.matches("old-password", stored.getValue()));
        assertNotEquals("brand-new-pass", stored.getValue());
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmEndsSessionsBurnsOtherResetTokensAndVerifiesTheAddress() {
        when(userTokenService.consume("raw", UserTokenPurpose.PASSWORD_RESET)).thenReturn(tokenFor(user));

        service.confirm("raw", "brand-new-pass", "brand-new-pass");

        verify(refreshTokenService).deleteByUserId(7);
        verify(userTokenService).consumeOutstanding(7, UserTokenPurpose.PASSWORD_RESET);
        verify(userRepository).markEmailVerified(7, "jane@example.com");
    }

    @Test
    void confirmConsumesTheTokenBeforeWritingAnything() {
        when(userTokenService.consume("raw", UserTokenPurpose.PASSWORD_RESET)).thenReturn(tokenFor(user));

        service.confirm("raw", "brand-new-pass", "brand-new-pass");

        InOrder order = inOrder(userTokenService, userRepository);
        order.verify(userTokenService).consume("raw", UserTokenPurpose.PASSWORD_RESET);
        order.verify(userRepository).updatePassword(eq(7), anyString());
    }

    @Test
    void shortPasswordIsRejectedBeforeTheTokenIsTouched() {
        var ex = assertThrows(FieldValidationException.class, () -> service.confirm("raw", "1234567", "1234567"));

        assertEquals("password", ex.getField());
        assertEquals("Le mot de passe doit contenir au moins 8 caractères", ex.getMessage());
        verifyNoInteractions(userTokenService, refreshTokenService);
        verify(userRepository, never()).updatePassword(any(), any());
    }

    @Test
    void aPasswordOver72BytesIsRejectedBeforeTheTokenIsTouched() {
        var twoByteChars = "é".repeat(40); // 40 characters, 80 UTF-8 bytes

        var ex = assertThrows(FieldValidationException.class, () -> service.confirm("raw", twoByteChars, twoByteChars));

        assertEquals("password", ex.getField());
        assertEquals("Le mot de passe ne doit pas dépasser 72 octets", ex.getMessage());
        verifyNoInteractions(userTokenService, refreshTokenService);
    }

    @Test
    void mismatchIsRejectedBeforeTheTokenIsTouched() {
        var ex = assertThrows(FieldValidationException.class, () -> service.confirm("raw", "brand-new-pass", "other-new-pass"));

        assertEquals("confirmedPassword", ex.getField());
        assertEquals("Les mots de passe ne correspondent pas", ex.getMessage());
        verifyNoInteractions(userTokenService, refreshTokenService);
        verify(userRepository, never()).updatePassword(any(), any());
    }

    @Test
    void anUnusableTokenIsInvalidAndChangesNothing() {
        when(userTokenService.consume("bad", UserTokenPurpose.PASSWORD_RESET)).thenThrow(new InvalidActionTokenException());

        assertThrows(InvalidActionTokenException.class, () -> service.confirm("bad", "brand-new-pass", "brand-new-pass"));

        verify(userRepository, never()).updatePassword(any(), any());
        verifyNoInteractions(refreshTokenService);
    }
}
