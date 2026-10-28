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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    void missingMediaThrowsNotFoundForAdmin() {
        when(mediaRepository.findById(5)).thenReturn(Optional.empty());

        assertThrows(com.bowe.meetstudent.exceptions.ResourceNotFoundException.class,
                () -> mediaService.getAccessibleMedia(5, principal(99, "ROLE_ADMIN")));
    }

    @Test
    void missingMediaIsIndistinguishableFromForbiddenForNonAdmin() {
        when(mediaRepository.findById(5)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> mediaService.getAccessibleMedia(5, principal(7, "ROLE_STUDENT")));
    }

    @Test
    void privateMediaRequiresAuthenticationForAnonymous() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> mediaService.getAccessibleMedia(5, null));
    }

    @Test
    void missingMediaRequiresAuthenticationForAnonymous() {
        when(mediaRepository.findById(5)).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> mediaService.getAccessibleMedia(5, null));
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

    private Media publicMedia(MediaCategory category, Integer ownerId) {
        Media m = Media.builder().visibility(MediaVisibility.PUBLIC)
                .category(category).ownerId(ownerId).storageKey("public/x.png").build();
        ReflectionTestUtils.setField(m, "id", 5);
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void deletePublicMediaByOtherUserIsForbidden() throws IOException {
        publicMedia(MediaCategory.USER_PHOTO, 7);

        assertThrows(AccessDeniedException.class,
                () -> mediaService.delete(5, principal(8, "ROLE_STUDENT")));

        verify(storageService, never()).delete(any());
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void deleteOwnerlessPublicMediaByStudentIsForbidden() throws IOException {
        publicMedia(MediaCategory.SCHOOL_LOGO, null);

        assertThrows(AccessDeniedException.class,
                () -> mediaService.delete(5, principal(8, "ROLE_STUDENT")));

        verify(storageService, never()).delete(any());
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void deleteOwnPublicMediaIsAllowed() throws IOException {
        Media m = publicMedia(MediaCategory.USER_PHOTO, 7);

        mediaService.delete(5, principal(7, "ROLE_STUDENT"));

        verify(storageService).delete("public/x.png");
        verify(mediaRepository).delete(m);
    }

    @Test
    void deleteAnyPublicMediaByAdminIsAllowed() throws IOException {
        Media m = publicMedia(MediaCategory.SCHOOL_LOGO, null);

        mediaService.delete(5, principal(99, "ROLE_ADMIN"));

        verify(storageService).delete("public/x.png");
        verify(mediaRepository).delete(m);
    }

    @Test
    void deletePrivateMediaByAdminIsAllowed() throws IOException {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).storageKey("private/x.pdf").build();
        when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        mediaService.delete(5, principal(99, "ROLE_ADMIN"));

        verify(storageService).delete("private/x.pdf");
        verify(mediaRepository).delete(m);
    }

    @Test
    void deleteUnknownMediaIsForbiddenForNonAdminAndNotFoundForAdmin() {
        when(mediaRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> mediaService.delete(404, principal(8, "ROLE_STUDENT")));
        assertThrows(com.bowe.meetstudent.exceptions.ResourceNotFoundException.class,
                () -> mediaService.delete(404, principal(99, "ROLE_ADMIN")));
    }

    @Test
    void deleteAnonymouslyIsUnauthenticatedForPublicPrivateAndUnknown() {
        Media pub = publicMedia(MediaCategory.USER_PHOTO, 7);
        when(mediaRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.authentication.InsufficientAuthenticationException.class,
                () -> mediaService.delete(5, null));
        assertThrows(org.springframework.security.authentication.InsufficientAuthenticationException.class,
                () -> mediaService.delete(404, null));
        assertNotNull(pub);
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

    // --- deleteAllOwnedBy: files go only after commit, best-effort ---

    private Media owned(String key) {
        return Media.builder().storageKey(key).build();
    }

    @Test
    void deleteAllOwnedByKeepsFilesUntilCommit() throws IOException {
        var a = owned("private/a.pdf");
        var b = owned("private/b.pdf");
        when(mediaRepository.findByOwnerId(5)).thenReturn(List.of(a, b));
        TransactionSynchronizationManager.initSynchronization();
        try {
            mediaService.deleteAllOwnedBy(5);

            verify(mediaRepository).deleteAll(List.of(a, b));
            verify(storageService, never()).delete(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(storageService).delete("private/a.pdf");
            verify(storageService).delete("private/b.pdf");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteAllOwnedByNeverTouchesFilesOnRollback() throws IOException {
        when(mediaRepository.findByOwnerId(5)).thenReturn(List.of(owned("private/a.pdf")));
        TransactionSynchronizationManager.initSynchronization();
        try {
            mediaService.deleteAllOwnedBy(5);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(storageService, never()).delete(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteAllOwnedByDeletesImmediatelyWithoutATransaction() throws IOException {
        when(mediaRepository.findByOwnerId(5)).thenReturn(List.of(owned("private/a.pdf")));

        mediaService.deleteAllOwnedBy(5);

        verify(storageService).delete("private/a.pdf");
    }

    @Test
    void deleteAllOwnedByContinuesWhenTheStorageThrows() throws IOException {
        when(mediaRepository.findByOwnerId(5)).thenReturn(
                List.of(owned("private/a.pdf"), owned("private/b.pdf"), owned("private/c.pdf")));
        doThrow(new IOException("disk")).when(storageService).delete("private/a.pdf");
        doThrow(new IllegalStateException("boom")).when(storageService).delete("private/b.pdf");
        TransactionSynchronizationManager.initSynchronization();
        try {
            mediaService.deleteAllOwnedBy(5);

            assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit));

            verify(storageService).delete("private/c.pdf");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // --- deleteByIdsAfterCommit: rows now, files after commit ---

    private Media row(int id, String key) {
        return Media.builder().id(id).storageKey(key).build();
    }

    @Test
    void deleteByIdsAfterCommitKeepsFilesUntilCommit() throws IOException {
        var logo = row(10, "public/logo.png");
        var cover = row(11, "public/cover.png");
        when(mediaRepository.findById(10)).thenReturn(Optional.of(logo));
        when(mediaRepository.findById(11)).thenReturn(Optional.of(cover));
        TransactionSynchronizationManager.initSynchronization();
        try {
            mediaService.deleteByIdsAfterCommit(java.util.Arrays.asList(10, null, 11, 10, 99));

            verify(mediaRepository).deleteAll(List.of(logo, cover));
            verify(storageService, never()).delete(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(storageService).delete("public/logo.png");
            verify(storageService).delete("public/cover.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteByIdsAfterCommitNeverTouchesFilesOnRollback() throws IOException {
        when(mediaRepository.findById(10)).thenReturn(Optional.of(row(10, "public/logo.png")));
        TransactionSynchronizationManager.initSynchronization();
        try {
            mediaService.deleteByIdsAfterCommit(List.of(10));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(storageService, never()).delete(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteByIdsAfterCommitDeletesImmediatelyWithoutATransaction() throws IOException {
        when(mediaRepository.findById(10)).thenReturn(Optional.of(row(10, "public/logo.png")));

        mediaService.deleteByIdsAfterCommit(List.of(10));

        verify(storageService).delete("public/logo.png");
    }

    @Test
    void deleteByIdsAfterCommitIgnoresNullsAndUnknownIds() throws IOException {
        mediaService.deleteByIdsAfterCommit(java.util.Arrays.asList(null, 99));

        verify(storageService, never()).delete(any());
    }
}
