package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.services.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.awt.*;
import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media") // Base path tailored to your request
@Tag(name = "Media management", description = "Endpoints for uploading media files")
public class MediaController {
    private final MediaService mediaService;

    @PostMapping(path = "/{entityType}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Uplaod a media file",
        description = "Uploads a file for a specific entity type (e.g., schools, users) and returns the public URL."
    )
    public ResponseEntity<Map<String, String>> uploadMedia(
        @Parameter(description = "The type of entity (schools, users, courses, programs)", required = true)
        @PathVariable String entityType,

        @Parameter(description = "The media file to upload", required = true)
        @RequestParam("file")MultipartFile file
        ) {
        try{
            // Let check for valid entity types to prevent spam folders
            if(!entityType.matches("^(shools|users|courses|programs)$")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid entity type. Allowed: schools, users, courses, programs"));
            }
            String relativePath = mediaService.saveMedia(file, entityType);
            // Construct the full URL for the frontend to access the static resource
            // This relies on the WebConfig mapping /uploads/** to the file system
            String fileUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/uploads/")
                .path(relativePath)
                .toUriString();
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", fileUrl));
        }catch (IOException ioException){
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Could not upload file: " + ioException.getMessage())
            );
        }
    }
}
