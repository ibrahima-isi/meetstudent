package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.MailTestResponse;
import com.bowe.meetstudent.exceptions.ErrorResponse;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.MailTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SMTP self-test. ADMIN only (see {@code /api/v1/admin/**} in WebSecurityConfig). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/mail")
@Tag(name = "14. Admin mail", description = "Check the SMTP configuration (ADMIN only)")
public class AdminMailController {

    private final MailTestService service;

    @PostMapping("/test")
    @Operation(summary = "Send a test email to the signed-in admin",
            description = "No body. Sends synchronously through the real mail sender, to the authenticated admin's own address only. "
                    + "The answer is the outcome only, never an address, host or credential. Limited to one test per admin every 10 s.")
    @ApiResponse(responseCode = "200", description = "Delivered to the SMTP server: {\"status\":\"SENT\"}")
    @ApiResponse(responseCode = "502", description = "Not delivered: {\"status\":\"AUTH_FAILED|CONNECTION_FAILED|TLS_FAILED|REJECTED|FAILED\"}")
    @ApiResponse(responseCode = "503", description = "Mail is disabled (MAIL_ENABLED=false): {\"status\":\"DISABLED\"}")
    @ApiResponse(responseCode = "429", description = "Cooldown, see Retry-After (seconds)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<MailTestResponse> test(@AuthenticationPrincipal UserPrincipal principal) {
        var outcome = service.sendTestTo(principal.getId());
        var status = switch (outcome) {
            case SENT -> HttpStatus.OK;
            case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new MailTestResponse(outcome.name()));
    }
}
