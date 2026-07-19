package com.bowe.meetstudent.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {

    private static final Set<String> ALLOWED_ENTITY_TYPES = Set.of("schools", "users", "courses", "programs");
    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES_BY_EXTENSION = Map.of(
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "webp", Set.of("image/webp"),
            "pdf", Set.of("application/pdf"),
            "mp4", Set.of("video/mp4"),
            "webm", Set.of("video/webm"),
            "mov", Set.of("video/quicktime")
    );

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.max-upload-bytes:10485760}")
    private long maxUploadBytes;

    /**
     * save file by entity
     * @param file the file to save
     * @param entityType the entity
     * @return String
     * @throws IOException
     */
    public String saveMedia(MultipartFile file, String entityType) throws IOException {
        validateEntityType(entityType);
        String extension = validateFile(file);
        byte[] content = file.getBytes();
        validateFileContent(extension, content);

        Path uploadBasePath = getUploadBasePath();
        Path uploadPath = uploadBasePath.resolve(entityType).normalize();
        ensurePathInsideUploadDir(uploadPath);
        if(!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = UUID.randomUUID() + "." + extension;

        // Persisting the file
        Path filePath = uploadPath.resolve(fileName).normalize();
        ensurePathInsideUploadDir(filePath);
        Files.write(filePath, content);

        // return path or url of the saved file
        return entityType + "/" + fileName;

    }

    /**
     * Delete media file from filesystem
     * @param relativePath The relative path stored in DB (e.g., "schools/uuid.jpg")
     * @return boolean true if deleted
     * @throws IOException
     */
    public boolean deleteMedia(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) return false;
        
        Path filePath = getUploadBasePath().resolve(relativePath).normalize();
        ensurePathInsideUploadDir(filePath);
        return Files.deleteIfExists(filePath);
    }

    /**
     * Delete media file by its full URL or relative path
     * @param fileUrl The URL or relative path
     */
    public void deleteMediaByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return;

        try {
            // Extract relative path from URL if necessary
            // e.g., http://localhost:8080/uploads/schools/uuid.jpg -> schools/uuid.jpg
            String relativePath = fileUrl;
            if (fileUrl.contains("/uploads/")) {
                relativePath = fileUrl.substring(fileUrl.indexOf("/uploads/") + 9);
            }
            
            deleteMedia(relativePath);
        } catch (IOException e) {
            // Log error but don't fail the transaction
            System.err.println("Could not delete file: " + fileUrl + ". Error: " + e.getMessage());
        }
    }

    /**
     * Delete old media if it has been replaced by a new one
     * @param oldUrl The previous URL
     * @param newUrl The new URL
     */
    public void deleteOldMediaIfChanged(String oldUrl, String newUrl) {
        if (oldUrl != null && !oldUrl.isEmpty() && !oldUrl.equals(newUrl)) {
            deleteMediaByUrl(oldUrl);
        }
    }

    /**
     * Compare two lists of media and delete those that are no longer present
     * @param oldUrls List of previous URLs
     * @param newUrls List of new URLs
     */
    public void deleteRemovedMedia(java.util.List<String> oldUrls, java.util.List<String> newUrls) {
        if (oldUrls == null) return;
        for (String oldUrl : oldUrls) {
            if (newUrls == null || !newUrls.contains(oldUrl)) {
                deleteMediaByUrl(oldUrl);
            }
        }
    }

    public boolean isAllowedEntityType(String entityType) {
        return entityType != null && ALLOWED_ENTITY_TYPES.contains(entityType);
    }

    private void validateEntityType(String entityType) {
        if (!isAllowedEntityType(entityType)) {
            throw new IllegalArgumentException("Invalid entity type. Allowed: schools, users, courses, programs");
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("File exceeds the maximum allowed size");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        int extensionIndex = originalFilename.lastIndexOf(".");
        if (extensionIndex < 0 || extensionIndex == originalFilename.length() - 1) {
            throw new IllegalArgumentException("File extension is required");
        }

        String extension = originalFilename.substring(extensionIndex + 1).toLowerCase();
        Set<String> allowedMimeTypes = ALLOWED_MIME_TYPES_BY_EXTENSION.get(extension);
        if (allowedMimeTypes == null) {
            throw new IllegalArgumentException("File extension is not allowed");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!allowedMimeTypes.contains(contentType)) {
            throw new IllegalArgumentException("File type does not match an allowed media type");
        }

        return extension;
    }

    private void validateFileContent(String extension, byte[] content) {
        boolean valid = switch (extension) {
            case "jpg", "jpeg" -> startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "png" -> startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            case "webp" -> content.length >= 12
                    && startsWith(content, "RIFF".getBytes())
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            case "pdf" -> startsWith(content, "%PDF".getBytes());
            case "mp4" -> content.length >= 12
                    && content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p';
            case "webm" -> startsWith(content, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
            case "mov" -> content.length >= 12
                    && content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p'
                    && content[8] == 'q' && content[9] == 't';
            default -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException("File content does not match its declared type");
        }
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) return false;
        }
        return true;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) return "";
        int parameterIndex = contentType.indexOf(";");
        String normalized = parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType;
        return normalized.trim().toLowerCase();
    }

    private Path getUploadBasePath() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private void ensurePathInsideUploadDir(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(getUploadBasePath())) {
            throw new IOException("Invalid media path");
        }
    }

}
