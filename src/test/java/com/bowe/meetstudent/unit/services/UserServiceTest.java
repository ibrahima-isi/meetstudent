package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MediaService mediaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1);
        user.setFirstname("John");
        user.setLastname("Doe");
        user.setDiplomas(List.of("users/diploma1.pdf", "users/diploma2.pdf"));
    }

    @Test
    void testDeleteUser() {
        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));
        Mockito.doNothing().when(userRepository).deleteById(1);

        userService.deleteUser(1);

        Mockito.verify(mediaService).deleteMediaByUrl("users/diploma1.pdf");
        Mockito.verify(mediaService).deleteMediaByUrl("users/diploma2.pdf");
        Mockito.verify(userRepository).deleteById(1);
    }

    @Test
    void testPatch() {
        UserEntity updates = UserEntity.builder()
                .firstname("Johnny")
                .diplomas(List.of("users/diploma2.pdf", "users/diploma3.pdf"))
                .build();
        
        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        userService.patch(1, updates, passwordEncoder);

        assertEquals("Johnny", user.getFirstname());
        assertEquals(2, user.getDiplomas().size());
        Mockito.verify(mediaService).deleteRemovedMedia(any(), any());
    }
}
