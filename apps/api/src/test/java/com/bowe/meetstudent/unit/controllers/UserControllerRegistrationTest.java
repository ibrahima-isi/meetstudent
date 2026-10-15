package com.bowe.meetstudent.unit.controllers;

import com.bowe.meetstudent.controllers.UserController;
import com.bowe.meetstudent.dto.RegisterRequest;
import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.EmailVerificationService;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerRegistrationTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder encoder;
    @Mock private Mapper<UserEntity, UserDTO> userMapper;
    @Mock private RoleService roleService;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks
    private UserController controller;

    @Test
    void aRateLimitRefusalOfTheVerificationStepNeverFailsTheRegistration() {
        var request = RegisterRequest.builder().firstname("Jane").lastname("Doe").email("jane@example.com")
                .password("S3cure-test-pass").confirmedPassword("S3cure-test-pass").build();
        var saved = UserEntity.builder().email("jane@example.com").build();
        when(userService.isPasswordConfirmed(any(), any())).thenReturn(true);
        when(userService.emailNotExists(any())).thenReturn(true);
        when(userService.registerPublic(any(), isNull(), any())).thenReturn(saved);
        when(userMapper.toDTO(saved)).thenReturn(new UserDTO());
        doThrow(new com.bowe.meetstudent.exceptions.TooManyRequestsException("limit", 5))
                .when(emailVerificationService).sendVerification(any(), any());

        assertEquals(HttpStatus.CREATED, controller.saveUser(request).getStatusCode());
    }

    @Test
    void aFailureOfTheVerificationStepNeverFailsTheRegistration() {
        var request = RegisterRequest.builder().firstname("Jane").lastname("Doe").email("jane@example.com")
                .password("S3cure-test-pass").confirmedPassword("S3cure-test-pass").build();
        var saved = UserEntity.builder().email("jane@example.com").build();
        when(userService.isPasswordConfirmed(any(), any())).thenReturn(true);
        when(userService.emailNotExists(any())).thenReturn(true);
        when(userService.registerPublic(any(), isNull(), any())).thenReturn(saved);
        when(userMapper.toDTO(saved)).thenReturn(new UserDTO());
        doThrow(new IllegalStateException("db down")).when(emailVerificationService).sendVerification(any(), any());

        var response = controller.saveUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
