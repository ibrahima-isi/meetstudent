package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.services.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    private MediaStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new MediaStorageService();
        ReflectionTestUtils.setField(storage, "publicDir", tempDir.resolve("uploads").toString());
        ReflectionTestUtils.setField(storage, "privateDir", tempDir.resolve("storage/private").toString());
    }

    @Test
    void storePrivateWritesUnderPrivateDirAndReturnsPrefixedKey() throws IOException {
        String key = storage.store("hello".getBytes(), "pdf", MediaVisibility.PRIVATE);

        assertTrue(key.startsWith("private/"), key);
        assertTrue(key.endsWith(".pdf"), key);
        Path physical = tempDir.resolve("storage/private").resolve(key.substring("private/".length()));
        assertTrue(Files.exists(physical));
        // must NOT be under the statically served public dir
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve(key)));
    }

    @Test
    void storePublicWritesUnderPublicDir() throws IOException {
        String key = storage.store("img".getBytes(), "png", MediaVisibility.PUBLIC);

        assertTrue(key.startsWith("public/"), key);
        Path physical = tempDir.resolve("uploads").resolve(key);
        assertTrue(Files.exists(physical));
    }

    @Test
    void loadAsResourceReturnsStoredContent() throws IOException {
        String key = storage.store("data".getBytes(), "pdf", MediaVisibility.PRIVATE);

        Resource resource = storage.loadAsResource(key);

        assertTrue(resource.exists());
        assertEquals("data", new String(resource.getInputStream().readAllBytes()));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        String key = storage.store("x".getBytes(), "pdf", MediaVisibility.PRIVATE);

        assertTrue(storage.delete(key));
        assertThrows(IOException.class, () -> storage.loadAsResource(key));
    }

    @Test
    void loadRejectsPathTraversalKey() {
        assertThrows(IOException.class, () -> storage.loadAsResource("private/../../etc/passwd"));
    }
}
