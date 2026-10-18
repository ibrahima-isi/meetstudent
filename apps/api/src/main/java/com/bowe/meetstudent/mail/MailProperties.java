package com.bowe.meetstudent.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mail and account-token settings, bound from {@code app.mail.*}, {@code app.frontend-base-url} and
 * {@code app.tokens.*}. The token settings are read by the verification and reset features.
 */
@Getter
@ConfigurationProperties(prefix = "app")
public class MailProperties {

    /** Public origin of the web app, used to build links in emails. Never derived from the request. */
    private String frontendBaseUrl = "http://localhost:4200";

    /** Stripped once here so boot validation and the links in emails use the very same value. */
    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl == null ? null : frontendBaseUrl.strip();
    }

    private final Mail mail = new Mail();
    private final Tokens tokens = new Tokens();

    @Getter
    @Setter
    public static class Mail {
        /** Send through SMTP. Off by default: no SMTP server is configured at launch. */
        private boolean enabled = false;
        /**
         * {@code MAIL_PROVIDER}: brevo, gmail or ses fill in the SMTP connection defaults (see
         * {@link MailProviderPresets}); custom (default) leaves everything to the SPRING_MAIL_* variables.
         * Checked only when mail is enabled.
         */
        private String provider = "custom";
        /** {@code MAIL_SES_REGION}: AWS region of the SES SMTP endpoint, used only by the ses preset. */
        private String sesRegion = MailProviderPresets.DEFAULT_SES_REGION;
        /** Sender address (From header). Required when enabled. */
        private String from;
        /** Write emails, links included, to the log instead of sending. Local development only. */
        private boolean devLog = false;
        /** Send on a background executor. Tests turn this off to run synchronously. */
        private boolean async = true;
        /** Minimum seconds between two SMTP self-tests of the same admin ({@code POST /admin/mail/test}); 0 disables it. */
        private int testCooldownSeconds = 10;
    }

    @Getter
    @Setter
    public static class Tokens {
        private int resetTtlMinutes = 60;
        private int verifyTtlHours = 24;
        private int cooldownSeconds = 60;
        private int maxPerDay = 5;
        /** Tokens (any user, any purpose) issued for one address per 24 h; stops mail-bombing a third party. */
        private int maxPerAddressPerDay = 3;
    }
}
