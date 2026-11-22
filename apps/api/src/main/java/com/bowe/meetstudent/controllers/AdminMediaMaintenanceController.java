package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.MediaMaintenanceStatusDTO;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.services.MediaWriteGate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Operational controls and drain observability for media storage cutover and rollback.
 * ADMIN only (see {@code /api/v1/admin/**} in WebSecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/media/maintenance")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "15. Admin media maintenance", description = "Pause, resume, and observe draining of media and reference mutations (ADMIN only)")
public class AdminMediaMaintenanceController {

    private static final int MAX_WAIT_SECONDS = 120;

    private final MediaWriteGate mediaWriteGate;
    private final String storageBackend;

    public AdminMediaMaintenanceController(
            MediaWriteGate mediaWriteGate,
            @Value("${file.storage-backend:disk}") String storageBackend) {
        this.mediaWriteGate = mediaWriteGate;
        this.storageBackend = storageBackend;
    }

    @GetMapping
    @Operation(summary = "Inspect media write-pause and drain status")
    @ApiResponse(responseCode = "200", description = "Current pause and drain status")
    public MediaMaintenanceStatusDTO status() {
        return MediaMaintenanceStatusDTO.from(mediaWriteGate.status(), storageBackend);
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause media and reference mutations and optionally wait for in-flight writes and deferred deletions to drain")
    @ApiResponse(responseCode = "200", description = "Writes paused; check drained, inFlightWrites, and pendingDeferredDeletions")
    @ApiResponse(responseCode = "400", description = "waitSeconds out of range (0..120)")
    public MediaMaintenanceStatusDTO pause(@RequestParam(defaultValue = "0") long waitSeconds) {
        if (waitSeconds < 0 || waitSeconds > MAX_WAIT_SECONDS) {
            throw new FieldValidationException("waitSeconds", "waitSeconds must be between 0 and " + MAX_WAIT_SECONDS);
        }
        var status = waitSeconds == 0
                ? mediaWriteGate.pause()
                : mediaWriteGate.pauseAndAwaitDrain(Duration.ofSeconds(waitSeconds));
        return MediaMaintenanceStatusDTO.from(status, storageBackend);
    }

    @PostMapping("/resume")
    @Operation(summary = "Reopen media and reference mutations")
    @ApiResponse(responseCode = "200", description = "Writes resumed")
    public MediaMaintenanceStatusDTO resume() {
        return MediaMaintenanceStatusDTO.from(mediaWriteGate.resume(), storageBackend);
    }
}
