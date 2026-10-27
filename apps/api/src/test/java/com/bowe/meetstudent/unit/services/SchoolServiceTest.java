package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.services.MediaStorageService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bowe.meetstudent.repositories.TagRepository;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.SchoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private MediaService mediaService;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private MediaStorageService storageService;

    @InjectMocks
    private SchoolService schoolService;

    private School school;

    @BeforeEach
    void setUp() {
        school = new School();
        school.setId(1);
        school.setName("Test School");
        school.setLogoMediaId(10);
        school.setCoverMediaId(11);
    }

    private Media media(int id, String key) {
        return Media.builder().id(id).storageKey(key).build();
    }

    private final Media logo = media(10, "public/logo.png");
    private final Media cover = media(11, "public/cover.png");

    private void stubMedia() {
        Mockito.when(mediaRepository.findById(10)).thenReturn(Optional.of(logo));
        Mockito.when(mediaRepository.findById(11)).thenReturn(Optional.of(cover));
    }

    @Test
    void testDeleteClearsWishlistLinksBeforeDeletingTheSchool() {
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        stubMedia();

        schoolService.delete(1);

        var order = Mockito.inOrder(schoolRepository);
        order.verify(schoolRepository).deleteWishlistLinks(1);
        order.verify(schoolRepository).deleteById(1);
        Mockito.verify(mediaRepository).delete(logo);
        Mockito.verify(mediaRepository).delete(cover);
    }

    @Test
    void testDeleteKeepsTheFilesUntilTheTransactionCommits() throws Exception {
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        stubMedia();
        TransactionSynchronizationManager.initSynchronization();
        try {
            schoolService.delete(1);

            Mockito.verify(storageService, Mockito.never()).delete(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            Mockito.verify(storageService).delete("public/logo.png");
            Mockito.verify(storageService).delete("public/cover.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void testDeleteNeverDeletesFilesWhenTheDeleteFailsAndRollsBack() throws Exception {
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("fk"))
                .when(schoolRepository).deleteById(1);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> schoolService.delete(1));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            Mockito.verify(storageService, Mockito.never()).delete(any());
            Mockito.verify(mediaRepository, Mockito.never()).delete(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void testPatchReplacesLogoMediaAndDeletesOld() {
        School updates = School.builder().name("Updated School").logoMediaId(20).build();

        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.when(schoolRepository.save(any(School.class))).thenReturn(school);

        schoolService.patch(1, updates);

        assertEquals("Updated School", school.getName());
        assertEquals(20, school.getLogoMediaId());
        Mockito.verify(mediaService).deleteById(10); // old logo removed
        Mockito.verify(mediaService, Mockito.never()).deleteById(11); // cover unchanged
    }

    @Test
    void testPatchWithSameLogoMediaIdDoesNotDelete() {
        School updates = School.builder().logoMediaId(10).build();

        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.when(schoolRepository.save(any(School.class))).thenReturn(school);

        schoolService.patch(1, updates);

        Mockito.verify(mediaService, Mockito.never()).deleteById(any());
    }
}
