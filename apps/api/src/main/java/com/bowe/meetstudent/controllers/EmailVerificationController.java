package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.AcceptedResponse;
import com.bowe.meetstudent.dto.ConfirmTokenRequest;
import com.bowe.meetstudent.exceptions.ErrorResponse;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/email-verification")
@Tag(name = "01. Authentication", description = "Endpoints for user authentication")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/confirm")
    @Operation(summary = "Confirm an email address",
            description = "Public. Consumes the one-time token from the verification email. POST (not GET) so mail scanners that prefetch links cannot burn it.")
    @ApiResponse(responseCode = "204", description = "Email address confirmed")
    @ApiResponse(responseCode = "400", description = "Blank, unknown, expired, already used or outdated token: {\"token\": \"Lien invalide ou expiré\"}")
    public ResponseEntity<Void> confirm(@RequestBody @Validated ConfirmTokenRequest request) {
        emailVerificationService.confirm(request.getToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend")
    @Operation(summary = "Resend the verification email",
            description = "Requires a Bearer token. Sends a new link to the signed-in user's address and invalidates the previous one.")
    @ApiResponse(responseCode = "202", description = "Email queued", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AcceptedResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Address already verified", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Cooldown or daily limit; see the Retry-After header", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Email delivery is not available", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AcceptedResponse> resend(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Language of the email: fr (default) or en") @RequestParam(required = false) String lang) {
        emailVerificationService.resend(principal.getId(), lang);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AcceptedResponse.accepted());
    }
}
