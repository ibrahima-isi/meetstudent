package com.bowe.meetstudent.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** The single row of settings an admin edits from the back office. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "site_settings")
public class SiteSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "about_fr", nullable = false, length = 5000)
    private String aboutFr;

    @Column(name = "about_en", nullable = false, length = 5000)
    private String aboutEn;

    @Column(name = "whatsapp_url")
    private String whatsappUrl;

    @Column(name = "telegram_url")
    private String telegramUrl;

    @Column(name = "discord_url")
    private String discordUrl;

    @Column(name = "facebook_url")
    private String facebookUrl;

    @Column(name = "twitter_url")
    private String twitterUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "tiktok_url")
    private String tiktokUrl;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;
}
