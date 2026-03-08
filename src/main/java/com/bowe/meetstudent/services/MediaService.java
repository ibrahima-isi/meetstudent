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

}
