package com.bowe.meetstudent.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Production guard for {@code JWT_SECRET_KEY}: HMAC256 accepts any key, so a blank, short or published
 * example value would let anyone forge access tokens. Under the {@code prod} profile the application
 * refuses to start (the constructor throws) unless the secret is at least {@value #MIN_SECRET_BYTES}
 * bytes (UTF-8) and is not one of the values shipped in the repository. Other profiles keep their defaults.
 */
@Component
@Profile("prod")
public class JwtSecretStartupCheck {

    public static final int MIN_SECRET_BYTES = 32;

    private static final String HOW_TO = " Generate one with: openssl rand -base64 48";

    /** Lower-cased values that appear in the repository (examples, dev default, test profile). */
    private static final Set<String> KNOWN_EXAMPLES = Set.of(
            "change_me_to_a_secure_random_key",
            "change_me_generate_with_openssl_rand_base64_48",
            "devsecretkeyforlocalonly!1234567890",
            "thisisasecretkeyforintegrationtestingonly!12345");

    public JwtSecretStartupCheck(JwtProperties properties) {
        validate(properties.getSecretKey());
    }

    public static void validate(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_KEY must be set when running with the 'prod' profile." + HOW_TO);
        }
        if (KNOWN_EXAMPLES.contains(secret.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "JWT_SECRET_KEY is an example or placeholder value published in the repository; anyone could forge tokens."
                            + HOW_TO);
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET_KEY must be at least " + MIN_SECRET_BYTES + " bytes long." + HOW_TO);
        }
    }
}
