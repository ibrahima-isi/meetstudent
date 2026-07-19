package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.services.MediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaServiceTest {

    private static final long MAX_UPLOAD_BYTES = 10_485_760L;
    private static final byte[] JPEG_CONTENT = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};

    @TempDir
    Path tempDir;

    private MediaService newMediaService() {
        MediaService mediaService = new MediaService();
        ReflectionTestUtils.setField(mediaService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", MAX_UPLOAD_BYTES);
        return mediaService;
    }

    @Test
    void testSaveAndDeleteMedia() throws IOException {
        MediaService mediaService = newMediaService();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", JPEG_CONTENT);

        // Test Save
        String relativePath = mediaService.saveMedia(file, "schools");
        assertNotNull(relativePath);
        assertTrue(relativePath.startsWith("schools/"));
        assertTrue(Files.exists(tempDir.resolve(relativePath)));

        // Test Delete
        boolean deleted = mediaService.deleteMedia(relativePath);
        assertTrue(deleted);
        assertFalse(Files.exists(tempDir.resolve(relativePath)));
    }

    @Test
    void testSaveMediaRejectsFileExceedingMaxSize() {
        MediaService mediaService = newMediaService();
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", 5L);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", JPEG_CONTENT);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaService.saveMedia(file, "schools"));
        assertEquals("File exceeds the maximum allowed size", exception.getMessage());
    }

    @Test
    void testSaveMediaRejectsDisallowedExtension() {
        MediaService mediaService = newMediaService();

        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.exe", "application/octet-stream", JPEG_CONTENT);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaService.saveMedia(file, "schools"));
        assertEquals("File extension is not allowed", exception.getMessage());
    }

    @Test
    void testSaveMediaRejectsMimeTypeMismatch() {
        MediaService mediaService = newMediaService();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "application/pdf", JPEG_CONTENT);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaService.saveMedia(file, "schools"));
        assertEquals("File type does not match an allowed media type", exception.getMessage());
    }

    @Test
    void testSaveMediaRejectsContentNotMatchingDeclaredType() {
        MediaService mediaService = newMediaService();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "not a real jpeg".getBytes());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mediaService.saveMedia(file, "schools"));
        assertEquals("File content does not match its declared type", exception.getMessage());
    }

    @Test
    void testSaveMediaRejectsInvalidEntityType() {
        MediaService mediaService = newMediaService();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", JPEG_CONTENT);

        assertThrows(IllegalArgumentException.class,
                () -> mediaService.saveMedia(file, "../../etc"));
    }

    @Test
    void testDeleteMediaRejectsPathTraversal() {
        MediaService mediaService = newMediaService();

        assertThrows(IOException.class,
                () -> mediaService.deleteMedia("../outside.txt"));
    }

    @Test
    void testDeleteMediaByUrl() throws IOException {
        MediaService mediaService = newMediaService();

        // Create a dummy file
        Path schoolsDir = tempDir.resolve("schools");
        Files.createDirectories(schoolsDir);
        Path dummyFile = schoolsDir.resolve("dummy.jpg");
        Files.write(dummyFile, "content".getBytes());

        // Test deletion via full URL extraction
        String url = "http://localhost:8080/uploads/schools/dummy.jpg";
        mediaService.deleteMediaByUrl(url);

        assertFalse(Files.exists(dummyFile));
    }

    @Test
    void testDeleteOldMediaIfChanged() throws IOException {
        MediaService mediaService = newMediaService();

        Path schoolsDir = tempDir.resolve("schools");
        Files.createDirectories(schoolsDir);
        Path oldFile = schoolsDir.resolve("old.jpg");
        Files.write(oldFile, "content".getBytes());

        mediaService.deleteOldMediaIfChanged("schools/old.jpg", "schools/new.jpg");
        assertFalse(Files.exists(oldFile));
    }

    @Test
    void testDeleteRemovedMedia() throws IOException {
        MediaService mediaService = newMediaService();

        Path diplomaDir = tempDir.resolve("users");
        Files.createDirectories(diplomaDir);
        Path file1 = diplomaDir.resolve("file1.pdf");
        Path file2 = diplomaDir.resolve("file2.pdf");
        Files.write(file1, "content1".getBytes());
        Files.write(file2, "content2".getBytes());

        java.util.List<String> oldList = java.util.List.of("users/file1.pdf", "users/file2.pdf");
        java.util.List<String> newList = java.util.List.of("users/file2.pdf", "users/file3.pdf");

        mediaService.deleteRemovedMedia(oldList, newList);

        assertFalse(Files.exists(file1), "file1 should be deleted");
        assertTrue(Files.exists(file2), "file2 should still exist");
    }
}
