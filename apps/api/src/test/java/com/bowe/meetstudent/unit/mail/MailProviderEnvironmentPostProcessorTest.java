package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.mail.MailProviderEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Resolution of the preset defaults on a real {@link StandardEnvironment}: what the app would see for
 * {@code spring.mail.*} once the environment post-processor has run, with the properties the way application.yml
 * exposes them ({@code app.mail.*}).
 */
class MailProviderEnvironmentPostProcessorTest {

    private static StandardEnvironment environment(Map<String, Object> properties) {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", new LinkedHashMap<>(properties)));
        new MailProviderEnvironmentPostProcessor().postProcessEnvironment(env, null);
        return env;
    }

    @Test
    void brevoFillsEveryConnectionSetting() {
        var env = environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "brevo"));

        assertThat(env.getProperty("spring.mail.host")).isEqualTo("smtp-relay.brevo.com");
        assertThat(env.getProperty("spring.mail.port")).isEqualTo("587");
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.auth")).isEqualTo("true");
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("true");
    }

    /** The binder behind MailConfig accepts these as true, so the preset logic must see the same thing. */
    @Test
    void enabledIsReadWithTheSameConversionAsTheBinder() {
        for (var truthy : new String[]{"yes", "on", "1", "TRUE", " true "}) {
            var env = environment(Map.of("app.mail.enabled", truthy, "app.mail.provider", "brevo"));
            assertThat(env.getProperty("spring.mail.host")).as("MAIL_ENABLED=%s", truthy).isEqualTo("smtp-relay.brevo.com");
        }
        for (var falsy : new String[]{"no", "off", "0", "false", "", "maybe"}) {
            var env = environment(Map.of("app.mail.enabled", falsy, "app.mail.provider", "brevo"));
            assertThat(env.getProperty("spring.mail.host")).as("MAIL_ENABLED=%s", falsy).isNull();
        }
    }

    @Test
    void gmailFillsEveryConnectionSetting() {
        var env = environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "gmail"));

        assertThat(env.getProperty("spring.mail.host")).isEqualTo("smtp.gmail.com");
        assertThat(env.getProperty("spring.mail.port")).isEqualTo("587");
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("true");
    }

    @Test
    void sesUsesTheConfiguredRegionAndDefaultsToIreland() {
        var defaultRegion = environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "ses"));
        assertThat(defaultRegion.getProperty("spring.mail.host")).isEqualTo("email-smtp.eu-west-1.amazonaws.com");

        var other = environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "SES", "app.mail.ses-region", "us-east-1"));
        assertThat(other.getProperty("spring.mail.host")).isEqualTo("email-smtp.us-east-1.amazonaws.com");
        assertThat(other.getProperty("spring.mail.port")).isEqualTo("587");
    }

    @Test
    void anExplicitSpringMailSettingAlwaysWins() {
        var env = environment(Map.of(
                "app.mail.enabled", "true", "app.mail.provider", "brevo",
                "spring.mail.host", "relay.example.org",
                "spring.mail.port", "2525",
                "spring.mail.properties.mail.smtp.starttls.required", "false"));

        assertThat(env.getProperty("spring.mail.host")).isEqualTo("relay.example.org");
        assertThat(env.getProperty("spring.mail.port")).isEqualTo("2525");
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("false");
        // Not overridden: still the preset.
        assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).isEqualTo("true");
    }

    @Test
    void aBlankExplicitValueCountsAsUnset() {
        // An empty variable in .env (SPRING_MAIL_HOST=) must not hide the preset.
        var env = environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "gmail",
                "spring.mail.host", "", "spring.mail.port", "  "));

        assertThat(env.getProperty("spring.mail.host")).isEqualTo("smtp.gmail.com");
        assertThat(env.getProperty("spring.mail.port")).isEqualTo("587");
    }

    @Test
    void anExplicitValueGivenAsAnOsEnvironmentVariableWinsToo() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("app.mail.enabled", "true", "app.mail.provider", "brevo")));
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new org.springframework.core.env.SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of("SPRING_MAIL_HOST", "env-relay.example.org")));

        new MailProviderEnvironmentPostProcessor().postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.mail.host")).isEqualTo("env-relay.example.org");
        assertThat(env.getProperty("spring.mail.port")).isEqualTo("587");
    }

    @Test
    void nothingIsAppliedWhileMailIsDisabled() {
        var env = environment(Map.of("app.mail.enabled", "false", "app.mail.provider", "brevo"));
        assertThat(env.getProperty("spring.mail.host")).isNull();
        assertThat(env.getProperty("spring.mail.port")).isNull();

        var unset = environment(Map.of("app.mail.provider", "brevo"));
        assertThat(unset.getProperty("spring.mail.host")).isNull();
    }

    @Test
    void customOrUnsetProviderChangesNothing() {
        assertThat(environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "custom")).getProperty("spring.mail.host")).isNull();
        assertThat(environment(Map.of("app.mail.enabled", "true")).getProperty("spring.mail.host")).isNull();
        assertThat(environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "")).getProperty("spring.mail.host")).isNull();
    }

    @Test
    void anUnknownProviderOrRegionAppliesNothingAndDoesNotThrowHere() {
        // MailConfig reports it at boot, naming the variable.
        assertThatCode(() -> environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "sendgrid"))).doesNotThrowAnyException();
        assertThat(environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "sendgrid")).getProperty("spring.mail.host")).isNull();
        assertThat(environment(Map.of("app.mail.enabled", "true", "app.mail.provider", "ses", "app.mail.ses-region", "evil.com/x"))
                .getProperty("spring.mail.host")).isNull();
    }
}
