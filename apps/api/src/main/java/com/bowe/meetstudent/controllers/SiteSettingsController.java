package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.SiteSettingsDTO;
import com.bowe.meetstudent.dto.UpdateSiteSettingsRequest;
import com.bowe.meetstudent.entities.SiteSettings;
import com.bowe.meetstudent.services.SiteSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/site-settings")
@Tag(name = "14. Site settings", description = "Contact address, About text and social links shown on the public site, edited from the back office")
public class SiteSettingsController {

    private final SiteSettingsService siteSettingsService;

    @GetMapping
    @Operation(summary = "Read the site settings", description = "Public: the web's About and Contact pages read them.")
    @ApiResponse(responseCode = "200", description = "Settings returned")
    public ResponseEntity<SiteSettingsDTO> get() {
        return ResponseEntity.ok(toDTO(siteSettingsService.get()));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change the site settings", description = "Admin only. Absent fields are left unchanged. A blank social link (whatsappUrl, telegramUrl, discordUrl, facebookUrl, twitterUrl, instagramUrl, tiktokUrl) erases it.")
    @ApiResponse(responseCode = "200", description = "Settings updated")
    @ApiResponse(responseCode = "400", description = "Invalid email, blank or overlong text, or a social link that is not a full https URL (a blank link erases it)")
    public ResponseEntity<SiteSettingsDTO> update(@Valid @RequestBody UpdateSiteSettingsRequest request) {
        return ResponseEntity.ok(toDTO(siteSettingsService.update(request)));
    }

    private static SiteSettingsDTO toDTO(SiteSettings settings) {
        return new SiteSettingsDTO(settings.getContactEmail(), settings.getAboutFr(), settings.getAboutEn(),
                settings.getWhatsappUrl(), settings.getTelegramUrl(), settings.getDiscordUrl(), settings.getFacebookUrl(),
                settings.getTwitterUrl(), settings.getInstagramUrl(), settings.getTiktokUrl());
    }
}
