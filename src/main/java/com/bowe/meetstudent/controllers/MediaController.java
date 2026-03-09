package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.services.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media")
@Tag(name = "12. Media", description = "Endpoints for managing media uploads and storage")
public class MediaController {
    private final MediaService mediaService;

    @PostMapping(path = "/{entityType}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a media file",
        description = "Uploads a file for a specific entity type (e.g., schools, users) and returns the public URL."
    )
    @ApiResponse(responseCode = "201", description = "Media uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Invalid entity type or file")
    @ApiResponse(responseCode = "500", description = "Internal server error during upload")
    public ResponseEntity<Map<String, String>> uploadMedia(
        @Parameter(description = "The type of entity (schools, users, courses, programs)", required = true)
        @PathVariable String entityType,

        @Parameter(description = "The media file to upload", required = true)
        @RequestParam("file") MultipartFile file
        ) {
        try{
            if(!entityType.matches("^(schools|users|courses|programs)$")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid entity type. Allowed: schools, users, courses, programs"));
            }
            String relativePath = mediaService.saveMedia(file, entityType);
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
