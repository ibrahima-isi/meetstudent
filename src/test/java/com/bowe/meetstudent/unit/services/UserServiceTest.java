package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
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
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

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
        user.setDiplomas(new ArrayList<>(List.of("users/diploma1.pdf")));
        user.setCertificates(new ArrayList<>(List.of("users/cert1.pdf")));
        user.setPresentationVideoUrl("users/video.mp4");
        user.setWishlist(new ArrayList<>());
    }

    @Test
    void testDeleteUser() {
        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));
        Mockito.doNothing().when(userRepository).deleteById(1);

        userService.deleteUser(1);

        Mockito.verify(mediaService).deleteMediaByUrl("users/diploma1.pdf");
        Mockito.verify(mediaService).deleteMediaByUrl("users/cert1.pdf");
        Mockito.verify(mediaService).deleteMediaByUrl("users/video.mp4");
        Mockito.verify(userRepository).deleteById(1);
    }

    @Test
    void testPatch() {
        UserEntity updates = UserEntity.builder()
                .firstname("Johnny")
                .certificates(List.of("users/cert2.pdf"))
                .presentationVideoUrl("users/new-video.mp4")
                .build();
        
        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        userService.patch(1, updates, passwordEncoder);

        assertEquals("Johnny", user.getFirstname());
        assertEquals("users/new-video.mp4", user.getPresentationVideoUrl());
        Mockito.verify(mediaService).deleteRemovedMedia(any(), any());
        Mockito.verify(mediaService).deleteOldMediaIfChanged(any(), any());
    }

    @Test
    void testWishlistManagement() {
        School school = School.builder().id(10).name("Test School").build();
        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));
        Mockito.when(schoolRepository.findById(10)).thenReturn(Optional.of(school));
        Mockito.when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        userService.addToWishlist(1, 10);
        assertTrue(user.getWishlist().contains(school));

        userService.removeFromWishlist(1, 10);
        assertFalse(user.getWishlist().contains(school));
    }
}
