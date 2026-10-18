package com.bowe.meetstudent.mail;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Default {@code spring.mail.*} connection settings of the supported providers. Only defaults: an explicit
 * SPRING_MAIL_* variable always wins (see {@link MailProviderEnvironmentPostProcessor}). Credentials are never part
 * of a preset; they stay in SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD.
 * <p>
 * All three providers take authenticated submission on port 587 with mandatory STARTTLS (no silent downgrade).
 */
public final class MailProviderPresets {

    public static final String DEFAULT_SES_REGION = "eu-west-1";

    /** Shape of an AWS region name (eu-west-1, us-gov-west-1): it ends up in a hostname, so nothing else is accepted. */
    private static final Pattern SES_REGION = Pattern.compile("[a-z]{2}(?:-[a-z]+)+-\\d{1,2}");

    private MailProviderPresets() {
    }

    public static boolean isValidSesRegion(String region) {
        return region != null && SES_REGION.matcher(region).matches();
    }

    /** @return the defaults of the provider (empty for {@link MailProvider#CUSTOM}); {@code sesRegion} is used by SES only */
    public static Map<String, String> defaults(MailProvider provider, String sesRegion) {
        return switch (provider) {
            case CUSTOM -> Map.of();
            case BREVO -> submissionOnStartTls("smtp-relay.brevo.com");
            case GMAIL -> submissionOnStartTls("smtp.gmail.com");
            case SES -> submissionOnStartTls("email-smtp." + sesRegion + ".amazonaws.com");
        };
    }

    private static Map<String, String> submissionOnStartTls(String host) {
        return Map.of(
                "spring.mail.host", host,
                "spring.mail.port", "587",
                "spring.mail.properties.mail.smtp.auth", "true",
                "spring.mail.properties.mail.smtp.starttls.enable", "true",
                "spring.mail.properties.mail.smtp.starttls.required", "true");
    }
}
