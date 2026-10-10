package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.CapabilitiesDTO;
import com.bowe.meetstudent.mail.EmailSender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "01. Authentication", description = "Endpoints for user authentication")
public class CapabilitiesController {

    private final EmailSender emailSender;

    @GetMapping("/capabilities")
    @Operation(summary = "Report optional server capabilities",
            description = "Public. Tells the web app whether account emails can be delivered, so it never claims an email was sent while SMTP is off.")
    @ApiResponse(responseCode = "200", description = "Capabilities", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CapabilitiesDTO.class)))
    public CapabilitiesDTO capabilities() {
        return new CapabilitiesDTO(emailSender.isEnabled());
    }
}
