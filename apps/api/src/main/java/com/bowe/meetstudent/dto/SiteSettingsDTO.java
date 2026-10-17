package com.bowe.meetstudent.dto;

/** The three texts plus the seven optional social links (null when not set). */
public record SiteSettingsDTO(
        String contactEmail,
        String aboutFr,
        String aboutEn,
        String whatsappUrl,
        String telegramUrl,
        String discordUrl,
        String facebookUrl,
        String twitterUrl,
        String instagramUrl,
        String tiktokUrl
) {
}
