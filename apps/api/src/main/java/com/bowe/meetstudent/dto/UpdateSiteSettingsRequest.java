package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.dto.validation.HttpsUrlOrBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Every field is optional: an absent (null) field is left unchanged, so one
 * language can be edited without resending the other. Values are stripped
 * before validation, which is why a blank one fails the minimum size.
 *
 * <p>The seven social links are the exception: a blank link (empty after stripping) is valid and
 * erases the stored one, while null still means unchanged. A filled link must be a full https URL.
 */
public record UpdateSiteSettingsRequest(
        @Email(message = "contactEmail must be a valid email address")
        @Size(min = 1, max = 255, message = "contactEmail must be between 1 and 255 characters")
        String contactEmail,

        @Size(min = 1, max = 5000, message = "aboutFr must be between 1 and 5000 characters")
        String aboutFr,

        @Size(min = 1, max = 5000, message = "aboutEn must be between 1 and 5000 characters")
        String aboutEn,

        @Size(max = 255, message = "whatsappUrl must be at most 255 characters")
        @HttpsUrlOrBlank(message = "whatsappUrl must be a full https URL (https://host/...) without spaces")
        String whatsappUrl,

        @Size(max = 255, message = "telegramUrl must be at most 255 characters")
        @HttpsUrlOrBlank(message = "telegramUrl must be a full https URL (https://host/...) without spaces")
        String telegramUrl,

        @Size(max = 255, message = "discordUrl must be at most 255 characters")
        @HttpsUrlOrBlank(message = "discordUrl must be a full https URL (https://host/...) without spaces")
        String discordUrl,

        @Size(max = 255, message = "facebookUrl must be at most 255 characters")
        @HttpsUrlOrBlank(message = "facebookUrl must be a full https URL (https://host/...) without spaces")
        String facebookUrl,

        @Size(max = 255, message = "twitterUrl must be at most 255 characters")
        @HttpsUrlOrBlank(message = "twitterUrl must be a full https URL (https://host/...) without spaces")
        String twitterUrl,

        @Size(max = 255, message = "instagramUrl must be at most 255 characters")
        @HttpsUrlOrBlank(message = "instagramUrl must be a full https URL (https://host/...) without spaces")
        String instagramUrl,

        @Size(max = 255, message = "tiktokUrl must be at most 255 characters")
        @HttpsUrlOrBlank(message = "tiktokUrl must be a full https URL (https://host/...) without spaces")
        String tiktokUrl
) {
    public UpdateSiteSettingsRequest {
        contactEmail = contactEmail == null ? null : contactEmail.strip();
        aboutFr = aboutFr == null ? null : aboutFr.strip();
        aboutEn = aboutEn == null ? null : aboutEn.strip();
        whatsappUrl = whatsappUrl == null ? null : whatsappUrl.strip();
        telegramUrl = telegramUrl == null ? null : telegramUrl.strip();
        discordUrl = discordUrl == null ? null : discordUrl.strip();
        facebookUrl = facebookUrl == null ? null : facebookUrl.strip();
        twitterUrl = twitterUrl == null ? null : twitterUrl.strip();
        instagramUrl = instagramUrl == null ? null : instagramUrl.strip();
        tiktokUrl = tiktokUrl == null ? null : tiktokUrl.strip();
    }
}
