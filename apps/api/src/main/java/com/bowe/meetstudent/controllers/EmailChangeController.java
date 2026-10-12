package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.AcceptedResponse;
import com.bowe.meetstudent.dto.ConfirmTokenRequest;
import com.bowe.meetstudent.dto.EmailChangeRequest;
import com.bowe.meetstudent.dto.PendingEmailResponse;
import com.bowe.meetstudent.exceptions.ErrorResponse;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.EmailChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Changing the account's email address. The owner-only endpoints sit under {@code /users/{id}} (signed in,
 * and only for one's own account: not even an admin acts here, the current password is the owner's); the
 * confirmation sits under {@code /auth} because the link is opened from a mail client, possibly signed out.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "03. Users", description = "Endpoints for managing user accounts (Admins, Students, Experts)")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    @PostMapping("/users/{id}/email-change")
    @Operation(summary = "Ask to change the email address",
            description = "Mails a confirmation link to the new address. The account keeps its current address (and sign-in) until the link is opened. A new request replaces the previous pending one.")
    @ApiResponse(responseCode = "202", description = "Link queued", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AcceptedResponse.class)))
    @ApiResponse(responseCode = "400", description = "{\"currentPassword\"|\"newEmail\": message}: wrong password, malformed, unchanged or already used address")
    @ApiResponse(responseCode = "403", description = "Not your account", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Cooldown or daily limit; see the Retry-After header", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Email delivery is not available", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AcceptedResponse> request(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the signed-in user") @PathVariable int id,
            @RequestBody @Validated EmailChangeRequest request) {
        requireOwner(principal, id);
        emailChangeService.request(id, request.getNewEmail(), request.getCurrentPassword(), request.getLang());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AcceptedResponse.accepted());
    }

    @GetMapping("/users/{id}/email-change")
    @Operation(summary = "Get the pending email change",
            description = "{\"pendingEmail\": \"new@example.com\"} while a change awaits its link, {} otherwise.")
    public PendingEmailResponse pending(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the signed-in user") @PathVariable int id) {
        requireOwner(principal, id);
        return new PendingEmailResponse(emailChangeService.pending(id).orElse(null));
    }

    @DeleteMapping("/users/{id}/email-change")
    @Operation(summary = "Cancel the pending email change", description = "Its link stops working. Harmless when nothing is pending.")
    @ApiResponse(responseCode = "204", description = "Cancelled")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the signed-in user") @PathVariable int id) {
        requireOwner(principal, id);
        emailChangeService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/email-change/resend")
    @Operation(summary = "Resend the confirmation link of the pending change", description = "The previous link stops working.")
    @ApiResponse(responseCode = "202", description = "Link queued", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AcceptedResponse.class)))
    @ApiResponse(responseCode = "409", description = "No change is pending", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Cooldown or daily limit; see the Retry-After header", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Email delivery is not available", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AcceptedResponse> resend(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the signed-in user") @PathVariable int id,
            @Parameter(description = "Language of the email: fr (default) or en") @RequestParam(required = false) String lang) {
        requireOwner(principal, id);
        emailChangeService.resend(id, lang);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AcceptedResponse.accepted());
    }

    @PostMapping("/auth/email-change/confirm")
    @Operation(summary = "Confirm a new email address",
            description = "Public. Consumes the one-time token from the email sent to the new address and switches the account to it. POST (not GET) so mail scanners that prefetch links cannot burn it.")
    @ApiResponse(responseCode = "204", description = "Address changed")
    @ApiResponse(responseCode = "400", description = "Blank, unknown, expired, already used or outdated token: {\"token\": \"Lien invalide ou expiré\"}")
    public ResponseEntity<Void> confirm(@RequestBody @Validated ConfirmTokenRequest request) {
        emailChangeService.confirm(request.getToken());
        return ResponseEntity.noContent().build();
    }

    private static void requireOwner(UserPrincipal principal, int userId) {
        if (principal == null || !principal.getId().equals(userId)) {
            throw new AccessDeniedException("You can only change the email address of your own account.");
        }
    }
}
