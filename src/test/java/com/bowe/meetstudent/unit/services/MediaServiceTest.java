package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Mock MediaStorageService storageService;
    @Mock MediaRepository mediaRepository;
    @InjectMocks MediaService mediaService;

    private UserPrincipal principal(Integer id, String role) {
        return UserPrincipal.builder().id(id).username("u@x.com")
                .authorities(List.of(new SimpleGrantedAuthority(role))).build();
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG);
    }

    private void wireStorageAndSave() throws IOException {
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", 10_485_760L);
        when(storageService.store(any(), any(), any())).thenReturn("private/uuid.jpg");
        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- Task 4: upload + idempotency ---

    @Test
    void uploadPersonalDocumentAsStudentPersistsPendingMediaOwnedByPrincipal() throws IOException {
        wireStorageAndSave();
        when(mediaRepository.findByOwnerIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());

        Media saved = mediaService.upload(jpeg(), MediaCategory.DIPLOMA, principal(7, "ROLE_STUDENT"), "key-1");

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        Media persisted = captor.getValue();
        assertEquals(7, persisted.getOwnerId());
        assertEquals(MediaCategory.DIPLOMA, persisted.getCategory());
        assertEquals(MediaVisibility.PRIVATE, persisted.getVisibility());
        assertEquals(VerificationStatus.PENDING, persisted.getVerificationStatus());
        assertEquals("key-1", persisted.getIdempotencyKey());
        assertEquals("photo.jpg", persisted.getOriginalFilename());
        assertNotNull(saved);
    }

    @Test
    void uploadSchoolLogoAsStudentIsForbidden() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> mediaService.upload(jpeg(), MediaCategory.SCHOOL_LOGO, principal(7, "ROLE_STUDENT"), null));
        assertNotNull(ex);
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadSchoolLogoAsAdminHasNoOwnerAndNoStatus() throws IOException {
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", 10_485_760L);
        when(storageService.store(any(), any(), any())).thenReturn("public/uuid.png");
        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        Media saved = mediaService.upload(
                new MockMultipartFile("file", "logo.png",
                        "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0}),
                MediaCategory.SCHOOL_LOGO, principal(9, "ROLE_ADMIN"), null);

        assertNull(saved.getOwnerId());
        assertNull(saved.getVerificationStatus());
        assertEquals(MediaVisibility.PUBLIC, saved.getVisibility());
    }

    @Test
    void uploadWithExistingIdempotencyKeyReturnsExistingMediaWithoutStoring() throws IOException {
        Media existing = Media.builder().storageKey("private/old.jpg")
                .category(MediaCategory.DIPLOMA).visibility(MediaVisibility.PRIVATE)
                .ownerId(7).idempotencyKey("key-1").build();
        when(mediaRepository.findByOwnerIdAndIdempotencyKey(7, "key-1")).thenReturn(Optional.of(existing));

        Media result = mediaService.upload(jpeg(), MediaCategory.DIPLOMA, principal(7, "ROLE_STUDENT"), "key-1");

        assertSame(existing, result);
        verifyNoInteractions(storageService);
        verify(mediaRepository, never()).save(any());
    }

    @Test
    void uploadRejectsContentNotMatchingDeclaredType() {
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", 10_485_760L);
        MockMultipartFile bad = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "not a jpeg".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> mediaService.upload(bad, MediaCategory.DIPLOMA, principal(7, "ROLE_STUDENT"), null));
    }

    // --- Task 5: download authorization ---

    @Test
    void publicMediaIsAccessibleToAnonymous() {
        Media m = Media.builder().visibility(MediaVisibility.PUBLIC).category(MediaCategory.SCHOOL_LOGO).build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertSame(m, mediaService.getAccessibleMedia(5, null));
    }

    @Test
    void privateMediaIsAccessibleToOwner() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertSame(m, mediaService.getAccessibleMedia(5, principal(7, "ROLE_STUDENT")));
    }

    @Test
    void privateMediaIsAccessibleToAdmin() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertSame(m, mediaService.getAccessibleMedia(5, principal(99, "ROLE_ADMIN")));
    }

    @Test
    void privateMediaIsForbiddenToOtherUser() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertThrows(AccessDeniedException.class,
                () -> mediaService.getAccessibleMedia(5, principal(8, "ROLE_STUDENT")));
    }

    @Test
    void missingMediaThrowsNotFound() {
        when(mediaRepository.findById(5)).thenReturn(Optional.empty());

        assertThrows(com.bowe.meetstudent.exceptions.ResourceNotFoundException.class,
                () -> mediaService.getAccessibleMedia(5, principal(7, "ROLE_STUDENT")));
    }

    // --- Task 6: moderation, delete ---

    @Test
    void setVerificationToRejectedStoresReason() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7)
                .verificationStatus(VerificationStatus.PENDING).build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));
        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        Media result = mediaService.setVerification(5, VerificationStatus.REJECTED, "blurry scan");

        assertEquals(VerificationStatus.REJECTED, result.getVerificationStatus());
        assertEquals("blurry scan", result.getRejectionReason());
    }

    @Test
    void setVerificationToVerifiedClearsReason() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7)
                .verificationStatus(VerificationStatus.REJECTED).rejectionReason("old").build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));
        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        Media result = mediaService.setVerification(5, VerificationStatus.VERIFIED, null);

        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        assertNull(result.getRejectionReason());
    }

    @Test
    void setVerificationToPendingIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> mediaService.setVerification(5, VerificationStatus.PENDING, null));
    }

    @Test
    void deleteByOwnerRemovesFileAndRow() throws Exception {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).storageKey("private/x.pdf").build();
        ReflectionTestUtils.setField(m, "id", 5);
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        mediaService.delete(5, principal(7, "ROLE_STUDENT"));

        verify(storageService).delete("private/x.pdf");
        verify(mediaRepository).delete(m);
    }

    @Test
    void deleteByOtherUserIsForbidden() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).storageKey("private/x.pdf").build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertThrows(AccessDeniedException.class,
                () -> mediaService.delete(5, principal(8, "ROLE_STUDENT")));
    }

    // --- deleteById / findById ---

    @Test
    void findByIdReturnsEmptyForNullId() {
        assertTrue(mediaService.findById(null).isEmpty());
        verifyNoInteractions(mediaRepository);
    }

    @Test
    void deleteByIdDeletesFileAndRow() throws IOException {
        Media media = Media.builder().storageKey("public/x.png")
                .visibility(MediaVisibility.PUBLIC).category(MediaCategory.SCHOOL_LOGO).build();
        ReflectionTestUtils.setField(media, "id", 9);
        when(mediaRepository.findById(9)).thenReturn(Optional.of(media));

        mediaService.deleteById(9);

        verify(storageService).delete("public/x.png");
        verify(mediaRepository).delete(media);
    }

    @Test
    void deleteByIdIsNoOpWhenNotFound() throws IOException {
        when(mediaRepository.findById(404)).thenReturn(Optional.empty());

        mediaService.deleteById(404);

        verify(storageService, never()).delete(any());
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void deleteByIdIsNoOpForNullId() throws IOException {
        mediaService.deleteById(null);
        verifyNoInteractions(mediaRepository);
        verify(storageService, never()).delete(any());
    }
}
