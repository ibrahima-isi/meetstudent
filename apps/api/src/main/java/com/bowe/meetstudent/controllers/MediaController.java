package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.MediaDTO;
import com.bowe.meetstudent.dto.MediaVerificationRequest;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.MediaStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media")
@Tag(name = "12. Media", description = "Upload, download, and moderation of media and documents")
public class MediaController {

    private final MediaService mediaService;
    private final MediaContentResponse mediaContentResponse;
    private final Mapper<Media, MediaDTO> mediaMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a media file",
            description = "Uploads a file for a category and returns its metadata (id, status). Send an Idempotency-Key header to make retries safe.")
    public ResponseEntity<MediaDTO> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("category") MediaCategory category,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) throws IOException {
        Media media = mediaService.upload(file, category, principal, idempotencyKey);
        return new ResponseEntity<>(mediaMapper.toDTO(media), HttpStatus.CREATED);
    }

    // MIME types safe to render inline in the browser. Anything else is forced to download.
    private static final Set<String> INLINE_SAFE_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp",
            MediaType.APPLICATION_PDF_VALUE);

    @GetMapping("/{id}")
    @Operation(summary = "Download a media file",
            description = "Public media is served to anyone; private media only to its owner or an admin.")
    @ApiResponse(responseCode = "200", description = "Media file",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public void download(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        Media media = mediaService.getAccessibleMedia(id, principal);

        String contentType = media.getContentType() != null
                ? media.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        // Only render a known-safe type inline; never trust the stored value to be renderable.
        boolean inline = INLINE_SAFE_TYPES.contains(contentType);
        String responseType = inline ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // RFC 6266 encoding neutralizes CR/LF and quote injection from the user-supplied filename.
        String filename = media.getOriginalFilename() != null ? media.getOriginalFilename() : "file";
        ContentDisposition disposition = ContentDisposition
                .builder(inline ? "inline" : "attachment")
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        MediaContentResponse.setSecurityHeaders(response);
        try {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
            mediaContentResponse.write(request, response, media.getStorageKey(), responseType, false);
        } catch (IOException e) {
            if (!response.isCommitted()) {
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, null);
                if (e instanceof MediaStorage.MissingObjectException) {
                    throw new ResourceNotFoundException("Media not found");
                }
            }
            throw e;
        }
    }

    @GetMapping("/mine")
    @Operation(summary = "List my media",
            description = "Returns the authenticated user's own media with verification status.")
    public ResponseEntity<List<MediaDTO>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        List<MediaDTO> dtos = mediaService.findOwnedBy(principal.getId()).stream()
                .map(mediaMapper::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Moderation queue",
            description = "Lists media filtered by verification status (admin).")
    public Page<MediaDTO> queue(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status,
            @ParameterObject Pageable pageable) {
        return mediaService.findByStatus(status, pageable).map(mediaMapper::toDTO);
    }

    @PatchMapping("/{id}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify or reject a document (admin)")
    public ResponseEntity<MediaDTO> verify(
            @PathVariable Integer id,
            @RequestBody @Validated MediaVerificationRequest request) {
        Media media = mediaService.setVerification(id, request.getStatus(), request.getReason());
        return ResponseEntity.ok(mediaMapper.toDTO(media));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a media file", description = "Owner or admin only.")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) throws IOException {
        mediaService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
