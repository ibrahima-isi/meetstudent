package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.AcceptedResponse;
import com.bowe.meetstudent.dto.ContactRequest;
import com.bowe.meetstudent.exceptions.ErrorResponse;
import com.bowe.meetstudent.security.ClientIpResolver;
import com.bowe.meetstudent.services.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contact")
@Tag(name = "15. Contact", description = "Public contact form: messages are stored and mailed to the team")
public class ContactController {

    private final ContactService contactService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @Operation(summary = "Send a contact message",
            description = "Public. Stores the message and mails it to the team after commit. A filled `website` field (honeypot) "
                    + "answers the same 202 and stores and sends nothing. Limited per client IP (default 5 per hour, honeypot hits included).")
    @ApiResponse(responseCode = "202", description = "Message accepted: {\"status\":\"accepted\"}")
    @ApiResponse(responseCode = "400", description = "Validation error: {field: message}")
    @ApiResponse(responseCode = "429", description = "Too many messages from this address; see Retry-After (seconds)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AcceptedResponse> send(@RequestBody @Valid ContactRequest request, HttpServletRequest http) {
        contactService.submit(request, clientIpResolver.resolve(http));
        return ResponseEntity.accepted().body(AcceptedResponse.accepted());
    }
}
