package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.UpdateSiteSettingsRequest;
import com.bowe.meetstudent.entities.SiteSettings;
import com.bowe.meetstudent.repositories.SiteSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SiteSettingsService {

    static final String DEFAULT_CONTACT_EMAIL = "contact@meetstudent.com";
    static final String DEFAULT_ABOUT_FR = """
            MeetStudent aide les futurs étudiants à choisir leur établissement au Sénégal.

            Nous réunissons au même endroit les écoles, instituts et universités, leurs formations et les avis de celles et ceux qui y étudient, pour que chacun puisse comparer avant de décider.

            Notre conviction est simple : une orientation réussie commence par une information claire, accessible à tous et partagée par la communauté étudiante.""";
    static final String DEFAULT_ABOUT_EN = """
            MeetStudent helps future students choose where to study in Senegal.

            We bring schools, institutes and universities together in one place, along with their programs and the reviews of the people who study there, so that everyone can compare before deciding.

            Our belief is simple: good guidance starts with clear information, open to all and shared by the student community.""";

    private final SiteSettingsRepository repository;
    private final Clock clock;

    /** The stored settings, or the starting values when the row does not exist (V19 inserts it on Postgres). */
    @Transactional(readOnly = true)
    public SiteSettings get() {
        return repository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettingsService::defaults);
    }

    /** Applies the fields present in the request; a null field keeps its current value, a blank social link erases it. */
    @Transactional
    public SiteSettings update(UpdateSiteSettingsRequest request) {
        var settings = repository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettingsService::defaults);
        if (request.contactEmail() != null) {
            settings.setContactEmail(request.contactEmail());
        }
        if (request.aboutFr() != null) {
            settings.setAboutFr(request.aboutFr());
        }
        if (request.aboutEn() != null) {
            settings.setAboutEn(request.aboutEn());
        }
        if (request.whatsappUrl() != null) {
            settings.setWhatsappUrl(blankToNull(request.whatsappUrl()));
        }
        if (request.telegramUrl() != null) {
            settings.setTelegramUrl(blankToNull(request.telegramUrl()));
        }
        if (request.discordUrl() != null) {
            settings.setDiscordUrl(blankToNull(request.discordUrl()));
        }
        if (request.facebookUrl() != null) {
            settings.setFacebookUrl(blankToNull(request.facebookUrl()));
        }
        if (request.twitterUrl() != null) {
            settings.setTwitterUrl(blankToNull(request.twitterUrl()));
        }
        if (request.instagramUrl() != null) {
            settings.setInstagramUrl(blankToNull(request.instagramUrl()));
        }
        if (request.tiktokUrl() != null) {
            settings.setTiktokUrl(blankToNull(request.tiktokUrl()));
        }
        settings.setModifiedAt(LocalDateTime.now(clock));
        return repository.save(settings);
    }

    /** Links arrive stripped: an empty one means "erase". */
    private static String blankToNull(String link) {
        return link.isBlank() ? null : link;
    }

    private static SiteSettings defaults() {
        return SiteSettings.builder()
                .id(SiteSettings.SINGLETON_ID)
                .contactEmail(DEFAULT_CONTACT_EMAIL)
                .aboutFr(DEFAULT_ABOUT_FR)
                .aboutEn(DEFAULT_ABOUT_EN)
                .build();
    }
}
