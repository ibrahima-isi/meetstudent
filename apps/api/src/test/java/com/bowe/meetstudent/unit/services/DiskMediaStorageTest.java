package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.services.DiskMediaStorage;
import com.bowe.meetstudent.services.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class DiskMediaStorageTest {

    @TempDir
    Path tempDir;

    private MediaStorage storage;

    @BeforeEach
    void setUp() {
        storage = new DiskMediaStorage(tempDir.resolve("uploads").toString(),
                tempDir.resolve("storage/private").toString());
    }

    @Test
    void writeKeepsPrivateDocumentsOnTheExistingPrivateVolume() throws IOException {
        storage.write("private/document.pdf", "hello".getBytes(US_ASCII), "application/pdf");

        assertTrue(Files.exists(tempDir.resolve("storage/private/document.pdf")));
        assertFalse(Files.exists(tempDir.resolve("uploads/private/document.pdf")));
    }

    @Test
    void writeKeepsPublicImagesOnTheExistingUploadVolume() throws IOException {
        storage.write("public/logo.png", "img".getBytes(US_ASCII), "image/png");

        assertTrue(Files.exists(tempDir.resolve("uploads/public/logo.png")));
    }

    @Test
    void openStreamsExistingDiskContent() throws IOException {
        storage.write("private/data.pdf", "data".getBytes(US_ASCII), "application/pdf");

        try (var input = storage.open("private/data.pdf")) {
            assertArrayEquals("data".getBytes(US_ASCII), input.readAllBytes());
        }
    }

    @Test
    void deleteIsIdempotent() throws IOException {
        String key = "private/document.pdf";
        storage.write(key, "x".getBytes(US_ASCII), "application/pdf");

        storage.delete(key);
        storage.delete(key);
        assertThrows(MediaStorage.MissingObjectException.class, () -> storage.open(key));
    }

    @Test
    void loadRejectsPathTraversalKey() {
        assertThrows(IOException.class, () -> storage.open("private/../../etc/passwd"));
    }
}
