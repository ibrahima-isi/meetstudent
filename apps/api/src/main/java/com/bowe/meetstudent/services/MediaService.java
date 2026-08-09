package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaStorageService storageService;
    private final MediaRepository mediaRepository;

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

    // --- Media entity orchestration ---

    @Transactional
    public Media upload(MultipartFile file, MediaCategory category,
                        UserPrincipal principal, String idempotencyKey) throws IOException {
        assertCanUpload(principal, category);

        Integer ownerId = category.isPersonalDocument() || category == MediaCategory.USER_PHOTO
                ? principal.getId()
                : null;

        if (idempotencyKey != null && !idempotencyKey.isBlank() && ownerId != null) {
            Optional<Media> existing = mediaRepository.findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String extension = validateFile(file);
        byte[] content = file.getBytes();
        validateFileContent(extension, content);

        String storageKey = storageService.store(content, extension, category.getVisibility());

        Media media = Media.builder()
                .storageKey(storageKey)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .category(category)
                .visibility(category.getVisibility())
                .ownerId(ownerId)
                .verificationStatus(category.isModerated() ? VerificationStatus.PENDING : null)
                .idempotencyKey(idempotencyKey)
                .build();

        return mediaRepository.save(media);
    }

    public void assertCanUpload(UserPrincipal principal, MediaCategory category) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required.");
        }
        boolean allowed = principal.getAuthorities().stream()
                .anyMatch(a -> category.getAllowedUploadRoles().contains(a.getAuthority()));
        if (!allowed) {
            throw new AccessDeniedException("You are not allowed to upload this media type.");
        }
    }

    public Media getAccessibleMedia(Integer mediaId, UserPrincipal principal) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

        if (media.getVisibility() == com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC) {
            return media;
        }
        boolean owner = principal != null && principal.getId() != null
                && principal.getId().equals(media.getOwnerId());
        if (owner || isAdmin(principal)) {
            return media;
        }
        throw new AccessDeniedException("You cannot access this document.");
    }

    public Resource loadContent(Media media) throws IOException {
        return storageService.loadAsResource(media.getStorageKey());
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal != null && principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @Transactional
    public Media setVerification(Integer mediaId, VerificationStatus status, String reason) {
        if (status == null || status == VerificationStatus.PENDING) {
            throw new IllegalArgumentException("Status must be VERIFIED or REJECTED.");
        }
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        media.setVerificationStatus(status);
        media.setRejectionReason(status == VerificationStatus.REJECTED ? reason : null);
        return mediaRepository.save(media);
    }

    public Page<Media> findByStatus(VerificationStatus status, Pageable pageable) {
        return mediaRepository.findByVerificationStatus(status, pageable);
    }

    public List<Media> findOwnedBy(Integer ownerId) {
        return mediaRepository.findByOwnerId(ownerId);
    }

    public List<Media> findByOwnerIdAndCategory(Integer ownerId, MediaCategory category) {
        return mediaRepository.findByOwnerIdAndCategory(ownerId, category);
    }

    @Transactional
    public void delete(Integer mediaId, UserPrincipal principal) throws IOException {
        Media media = getAccessibleMedia(mediaId, principal);
        storageService.delete(media.getStorageKey());
        mediaRepository.delete(media);
    }

    public Optional<Media> findById(Integer mediaId) {
        if (mediaId == null) return Optional.empty();
        return mediaRepository.findById(mediaId);
    }

    /**
     * Server-side delete of a media row and its file, without a principal check.
     * For orchestration by ROLE_ADMIN-gated entity endpoints (school/course/program
     * image replace + delete). No-op when the id is null or the row is gone.
     */
    @Transactional
    public void deleteById(Integer mediaId) {
        if (mediaId == null) return;
        mediaRepository.findById(mediaId).ifPresent(media -> {
            try {
                storageService.delete(media.getStorageKey());
            } catch (IOException e) {
                // best-effort file cleanup; the row is still removed
            }
            mediaRepository.delete(media);
        });
    }

    @Transactional
    public void deleteAllOwnedBy(Integer ownerId) {
        List<Media> owned = mediaRepository.findByOwnerId(ownerId);
        for (Media media : owned) {
            try {
                storageService.delete(media.getStorageKey());
            } catch (IOException e) {
                // best-effort file cleanup; the row is still removed
            }
        }
        mediaRepository.deleteAll(owned);
    }

}
