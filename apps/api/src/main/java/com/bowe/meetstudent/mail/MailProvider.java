package com.bowe.meetstudent.mail;

import java.util.Locale;
import java.util.Optional;

/** Value of {@code MAIL_PROVIDER}. */
public enum MailProvider {
    /** No preset: the connection comes from the SPRING_MAIL_* variables only (the behaviour before presets existed). */
    CUSTOM,
    BREVO,
    GMAIL,
    SES;

    /** The accepted values, for error messages. */
    public static final String ALLOWED = "brevo, gmail, ses, custom";

    /** Case-insensitive; null and blank mean {@link #CUSTOM}; anything else unknown is empty. */
    public static Optional<MailProvider> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(CUSTOM);
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "custom" -> Optional.of(CUSTOM);
            case "brevo" -> Optional.of(BREVO);
            case "gmail" -> Optional.of(GMAIL);
            case "ses" -> Optional.of(SES);
            default -> Optional.empty();
        };
    }
}
