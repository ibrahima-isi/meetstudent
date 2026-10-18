package com.bowe.meetstudent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

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
    private final Mail mail = new Mail();

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

    /** Delivery of the team mail ({@code app.contact.mail.*}). */
    @Getter
    @Setter
    public static class Mail {
        /** Hard bounds: a retry holds a pool thread, so neither the count nor the wait may be configured away. */
        public static final int MAX_ATTEMPTS_LIMIT = 5;
        public static final Duration MAX_DELAY = Duration.ofSeconds(60);

        private final Retry retry = new Retry();
    }

    /** Retry of transient failures (connection, TLS, unknown) inside the background listener. */
    @Getter
    @Setter
    public static class Retry {
        /** Total attempts including the first one; clamped to 1..{@link Mail#MAX_ATTEMPTS_LIMIT}. */
        private int maxAttempts = 3;
        /** Wait before the second attempt. */
        private Duration initialDelay = Duration.ofSeconds(2);
        /** Each further wait is the previous one times this (2 s, then 10 s with the default 5). */
        private int backoffMultiplier = 5;

        public int effectiveMaxAttempts() {
            return Math.max(1, Math.min(maxAttempts, Mail.MAX_ATTEMPTS_LIMIT));
        }

        /** Wait after the {@code attempt}-th failed attempt (1-based), never negative and capped at {@link Mail#MAX_DELAY}. */
        public Duration delayAfter(int attempt) {
            var base = initialDelay == null || initialDelay.isNegative() ? Duration.ZERO : initialDelay;
            var multiplier = Math.max(1, backoffMultiplier);
            var delay = base;
            for (int i = 1; i < attempt; i++) {
                delay = delay.multipliedBy(multiplier);
                if (delay.compareTo(Mail.MAX_DELAY) > 0) {
                    return Mail.MAX_DELAY;
                }
            }
            return delay.compareTo(Mail.MAX_DELAY) > 0 ? Mail.MAX_DELAY : delay;
        }
    }
}
