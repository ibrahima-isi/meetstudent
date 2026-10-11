package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.AcceptedResponse;
import com.bowe.meetstudent.dto.ForgotPasswordRequest;
import com.bowe.meetstudent.dto.ResetPasswordRequest;
import com.bowe.meetstudent.services.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "01. Authentication", description = "Endpoints for user authentication")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    @Operation(summary = "Ask for a password reset email",
            description = "Public. Always answers 202 for a well-formed address, whether or not an account exists, a limit applies or email delivery is disabled, so it cannot be used to discover accounts.")
    @ApiResponse(responseCode = "202", description = "Request taken into account", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AcceptedResponse.class)))
    @ApiResponse(responseCode = "400", description = "Blank or malformed email: {\"email\": \"...\"}")
    public ResponseEntity<AcceptedResponse> request(@RequestBody @Validated ForgotPasswordRequest request) {
        passwordResetService.request(request.getEmail(), request.getLang());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AcceptedResponse.accepted());
    }

    @PostMapping("/confirm")
    @Operation(summary = "Choose a new password",
            description = "Public. Consumes the one-time token from the reset email, sets the password and ends every refresh-token session of the account. Access tokens already issued stay valid until they expire.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "{\"password\": \"...\"} too short, {\"confirmedPassword\": \"...\"} different, or {\"token\": \"Lien invalide ou expiré\"} unknown, expired, used or outdated", content = @Content(mediaType = "application/json"))
    public ResponseEntity<Void> confirm(@RequestBody @Validated ResetPasswordRequest request) {
        passwordResetService.confirm(request.getToken(), request.getPassword(), request.getConfirmedPassword());
        return ResponseEntity.noContent().build();
    }
}
