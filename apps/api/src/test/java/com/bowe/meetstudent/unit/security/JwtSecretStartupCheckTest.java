package com.bowe.meetstudent.unit.security;

import com.bowe.meetstudent.security.JwtProperties;
import com.bowe.meetstudent.security.JwtSecretStartupCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The prod profile must refuse a JWT secret that is missing, short, or a published placeholder. */
class JwtSecretStartupCheckTest {

    private static String randomSecret() {
        var bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void rejectsBlankSecret(String secret) {
        var error = assertThrows(IllegalStateException.class, () -> JwtSecretStartupCheck.validate(secret));
        assertThat(error.getMessage()).contains("JWT_SECRET_KEY").contains("openssl rand -base64 48");
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        var error = assertThrows(IllegalStateException.class, () -> JwtSecretStartupCheck.validate("a".repeat(31)));
        assertThat(error.getMessage()).contains("32").contains("openssl rand -base64 48");
    }

    @Test
    void measuresBytesNotCharacters() {
        // 16 two-byte characters = 32 bytes: accepted. 15 of them = 30 bytes: rejected.
        assertDoesNotThrow(() -> JwtSecretStartupCheck.validate("é".repeat(16)));
        assertThrows(IllegalStateException.class, () -> JwtSecretStartupCheck.validate("é".repeat(15)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "change_me_to_a_secure_random_key",
            "CHANGE_ME_generate_with_openssl_rand_base64_48",
            "devSecretKeyForLocalOnly!1234567890",
            "ThisIsASecretKeyForIntegrationTestingOnly!12345",
            "  devSecretKeyForLocalOnly!1234567890  ",
            "DEVSECRETKEYFORLOCALONLY!1234567890"
    })
    void rejectsKnownExampleOrPlaceholderValues(String secret) {
        var error = assertThrows(IllegalStateException.class, () -> JwtSecretStartupCheck.validate(secret));
        assertThat(error.getMessage()).contains("example").contains("openssl rand -base64 48");
    }

    @Test
    void acceptsAStrongRandomSecret() {
        assertDoesNotThrow(() -> JwtSecretStartupCheck.validate(randomSecret()));
    }

    @Test
    void acceptsExactly32Bytes() {
        assertDoesNotThrow(() -> JwtSecretStartupCheck.validate("x7Kq2mP9vL4nR8sT1wY6zB3cD5fG0hJa"));
    }

    // ---- wiring: only the prod profile is guarded ----

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(JwtSecretStartupCheck.class)
    static class Config {
    }

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile));
    }

    @Test
    void prodContextFailsToStartWithTheDevDefault() {
        runner("prod").withPropertyValues("security.jwt.secret-key=devSecretKeyForLocalOnly!1234567890")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodContextFailsToStartWithoutSecret() {
        runner("prod").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodContextStartsWithAStrongSecret() {
        runner("prod").withPropertyValues("security.jwt.secret-key=" + randomSecret())
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void devContextKeepsItsDefaultSecret() {
        runner("dev").withPropertyValues("security.jwt.secret-key=devSecretKeyForLocalOnly!1234567890")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void testContextKeepsItsShortSecret() {
        runner("test").withPropertyValues("security.jwt.secret-key=short")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
