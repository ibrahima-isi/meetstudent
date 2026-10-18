package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.ContactProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds {@code app.contact.*} from the real {@code src/main/resources/application.yml} with the environment variables
 * of compose set but blank (compose passes {@code VAR:} through, so a blank value is a normal case, not an error).
 */
class ContactPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties(ContactProperties.class)
    static class Config {
    }

    private ApplicationContextRunner runner(String... env) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .withPropertyValues("spring.config.location=file:src/main/resources/application.yml")
                .withPropertyValues(env);
    }

    @Test
    void withoutAnyVariableTheDocumentedDefaultsApply() {
        runner().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var mail = ctx.getBean(ContactProperties.class).getMail();
            assertThat(mail.getRetry().effectiveMaxAttempts()).isEqualTo(3);
            assertThat(mail.getRetry().delayAfter(1)).isEqualTo(Duration.ofSeconds(2));
            assertThat(mail.getRetry().delayAfter(2)).isEqualTo(Duration.ofSeconds(10));
            assertThat(mail.effectivePendingTimeout()).isEqualTo(Duration.ofMinutes(15));
        });
    }

    @Test
    void presentButBlankRetryAndTimeoutVariablesKeepTheDefaultsInsteadOfFailingTheBinding() {
        runner("APP_CONTACT_MAIL_RETRY_MAX_ATTEMPTS=", "APP_CONTACT_MAIL_RETRY_BACKOFF_MULTIPLIER=",
                "APP_CONTACT_MAIL_RETRY_INITIAL_DELAY=", "APP_CONTACT_MAIL_PENDING_TIMEOUT=").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var mail = ctx.getBean(ContactProperties.class).getMail();
            assertThat(mail.getRetry().effectiveMaxAttempts()).isEqualTo(3);
            assertThat(mail.getRetry().delayAfter(3)).isEqualTo(Duration.ofSeconds(50));
            assertThat(mail.getRetry().delayAfter(1)).isEqualTo(Duration.ofSeconds(2));
            assertThat(mail.getRetry().delayAfter(2)).isEqualTo(Duration.ofSeconds(10));
            assertThat(mail.effectivePendingTimeout()).isEqualTo(Duration.ofMinutes(15));
        });
    }

    @Test
    void explicitValuesAreHonoured() {
        runner("APP_CONTACT_MAIL_RETRY_MAX_ATTEMPTS=2", "APP_CONTACT_MAIL_PENDING_TIMEOUT=30m").run(ctx -> {
            var mail = ctx.getBean(ContactProperties.class).getMail();
            assertThat(mail.getRetry().effectiveMaxAttempts()).isEqualTo(2);
            assertThat(mail.effectivePendingTimeout()).isEqualTo(Duration.ofMinutes(30));
        });
    }
}
