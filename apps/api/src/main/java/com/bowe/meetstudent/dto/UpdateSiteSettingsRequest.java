package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Every field is optional: an absent (null) field is left unchanged, so one
 * language can be edited without resending the other. Values are stripped
 * before validation, which is why a blank one fails the minimum size.
 */
public record UpdateSiteSettingsRequest(
        @Email(message = "contactEmail must be a valid email address")
        @Size(min = 1, max = 255, message = "contactEmail must be between 1 and 255 characters")
        String contactEmail,

        @Size(min = 1, max = 5000, message = "aboutFr must be between 1 and 5000 characters")
        String aboutFr,

        @Size(min = 1, max = 5000, message = "aboutEn must be between 1 and 5000 characters")
        String aboutEn
) {
    public UpdateSiteSettingsRequest {
        contactEmail = contactEmail == null ? null : contactEmail.strip();
        aboutFr = aboutFr == null ? null : aboutFr.strip();
        aboutEn = aboutEn == null ? null : aboutEn.strip();
    }
}
