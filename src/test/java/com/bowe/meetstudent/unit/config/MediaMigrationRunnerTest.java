package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.MediaMigrationRunner;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.repositories.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaMigrationRunnerTest {

    @TempDir
    Path tempDir;
    MediaRepository mediaRepository;
    MediaMigrationRunner runner;

    @BeforeEach
    void setUp() {
        mediaRepository = mock(MediaRepository.class);
        runner = new MediaMigrationRunner(mediaRepository);
        ReflectionTestUtils.setField(runner, "publicDir", tempDir.resolve("uploads").toString());
        ReflectionTestUtils.setField(runner, "privateDir", tempDir.resolve("storage/private").toString());
    }

    @Test
    void movesLegacyPrivateFileAndRewritesKey() throws Exception {
        Path legacy = tempDir.resolve("uploads/users/old.pdf");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, "doc".getBytes());

        Media media = Media.builder().storageKey("users/old.pdf")
                .visibility(MediaVisibility.PRIVATE).category(MediaCategory.DIPLOMA).ownerId(7).build();
        when(mediaRepository.findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility.PRIVATE, "private/"))
                .thenReturn(List.of(media));

        runner.run(null);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        String newKey = captor.getValue().getStorageKey();
        assertTrue(newKey.startsWith("private/"), newKey);
        assertTrue(Files.exists(tempDir.resolve("storage/private").resolve(newKey.substring("private/".length()))));
        assertFalse(Files.exists(legacy));
    }

    @Test
    void doesNothingWhenNoLegacyRows() throws Exception {
        when(mediaRepository.findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility.PRIVATE, "private/"))
                .thenReturn(List.of());

        runner.run(null);

        verify(mediaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
