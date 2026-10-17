package com.bowe.meetstudent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings of the public contact form, bound from {@code app.contact.*}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.contact")
public class ContactProperties {

    public static final String DEFAULT_RECIPIENT = "contact@meetstudent.com";

    /** Where the messages are mailed ({@code CONTACT_RECIPIENT}); blank falls back to {@link #DEFAULT_RECIPIENT}. */
    private String recipient = DEFAULT_RECIPIENT;

    /**
     * Take the client address from the first {@code X-Forwarded-For} hop. Only safe when every request comes
     * through a reverse proxy that rewrites that header (the prod profile); otherwise a client could forge it.
     */
    private boolean trustForwardedFor = false;

    private final RateLimit rateLimit = new RateLimit();

    public String effectiveRecipient() {
        return recipient == null || recipient.isBlank() ? DEFAULT_RECIPIENT : recipient.strip();
    }

    @Getter
    @Setter
    public static class RateLimit {
        /** Messages accepted per client IP and window (honeypot hits count too). */
        private int maxPerWindow = 5;
        private int windowMinutes = 60;
        /** Upper bound on the clients remembered at once; the least recently seen is dropped first. */
        private int maxTrackedClients = 10_000;
    }
}
