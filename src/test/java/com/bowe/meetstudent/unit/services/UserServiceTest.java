package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.RoleRepository;
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
import org.springframework.security.access.AccessDeniedException;
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
    private RoleRepository roleRepository;

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
    void registerStudent_shouldAssignStudentRole_whenRequestedRoleIsAdmin() {
        Role requestedRole = Role.builder().id(1).name("ROLE_ADMIN").build();
        Role studentRole = Role.builder().id(4).name("ROLE_STUDENT").build();
        UserEntity registration = UserEntity.builder()
                .firstname("Jane")
                .lastname("Student")
                .email("jane@example.com")
                .password("raw-password")
                .role(requestedRole)
                .build();

        Mockito.when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.of(studentRole));
        Mockito.when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        Mockito.when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity saved = userService.registerStudent(registration, passwordEncoder);

        assertEquals(studentRole, saved.getRole());
        assertEquals("encoded-password", saved.getPassword());
        Mockito.verify(userRepository).save(registration);
    }

    @Test
    void registerStudent_shouldFail_whenDefaultStudentRoleIsMissing() {
        UserEntity registration = UserEntity.builder().password("raw-password").build();
        Mockito.when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.registerStudent(registration, passwordEncoder));

        Mockito.verify(userRepository, Mockito.never()).save(any(UserEntity.class));
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
    void patch_shouldIgnoreRole_whenProfileOwnerSendsRoleUpdate() {
        Role currentRole = Role.builder().id(4).name("ROLE_STUDENT").build();
        Role requestedRole = Role.builder().id(1).name("ROLE_ADMIN").build();
        user.setRole(currentRole);
        UserEntity updates = UserEntity.builder().role(requestedRole).build();

        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        UserEntity saved = userService.patch(1, updates, passwordEncoder);

        assertEquals(currentRole, saved.getRole());
    }

    @Test
    void patchAsAdmin_shouldApplyRole_whenAdminSendsRoleUpdate() {
        Role currentRole = Role.builder().id(4).name("ROLE_STUDENT").build();
        Role requestedRole = Role.builder().id(3).name("ROLE_EXPERT").build();
        user.setRole(currentRole);
        UserEntity updates = UserEntity.builder().role(requestedRole).build();

        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));
        Mockito.when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        UserEntity saved = userService.patchAsAdmin(1, updates, passwordEncoder);

        assertEquals(requestedRole, saved.getRole());
    }

    @Test
    void resolveAuthenticatedUser_shouldReturnPrincipalUser_whenRequestedUserIsAbsent() {
        Mockito.when(userRepository.findById(1)).thenReturn(Optional.of(user));

        UserEntity resolved = userService.resolveAuthenticatedUser(1, null);

        assertEquals(user, resolved);
    }

    @Test
    void resolveAuthenticatedUser_shouldRejectMismatchedRequestedUser() {
        assertThrows(AccessDeniedException.class,
                () -> userService.resolveAuthenticatedUser(1, 2));

        Mockito.verify(userRepository, Mockito.never()).findById(any());
    }

    @Test
    void resolveAuthenticatedUser_shouldFail_whenPrincipalUserDoesNotExist() {
        Mockito.when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.resolveAuthenticatedUser(1, 1));
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
