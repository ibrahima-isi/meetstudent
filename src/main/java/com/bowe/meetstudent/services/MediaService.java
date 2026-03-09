package com.bowe.meetstudent.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class MediaService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * save file by entity
     * @param file the file to save
     * @param entityType the entity
     * @return String
     * @throws IOException
     */
    public String saveMedia(MultipartFile file, String entityType) throws IOException {
        // Create subfolders by type
        Path uploadPath = Paths.get(uploadDir, entityType);
        if(!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate a random name for each file
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = "";
        int i = originalFilename.lastIndexOf(".");
        if(i >= 0) {
            extension = originalFilename.substring(i);
        }
        String fileName = UUID.randomUUID().toString() + extension;

        // Persisting the file
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

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
        
        Path filePath = Paths.get(uploadDir).resolve(relativePath);
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

}
