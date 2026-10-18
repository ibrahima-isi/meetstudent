package com.bowe.meetstudent.mail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;

/**
 * Applies the {@code MAIL_PROVIDER} preset (brevo, gmail, ses) as defaults for the {@code spring.mail.*} settings,
 * before Boot builds the JavaMailSender. Runs after the configuration files are loaded, so it sees
 * {@code app.mail.*} exactly as the application does ({@code MAIL_ENABLED}, {@code MAIL_PROVIDER},
 * {@code MAIL_SES_REGION}).
 * <ul>
 *   <li>Only when {@code MAIL_ENABLED=true}: a disabled deployment is never touched or validated.</li>
 *   <li>Only the settings that are absent or blank are filled in: any explicit non-blank SPRING_MAIL_* value wins.</li>
 *   <li>An unknown provider or an invalid SES region applies nothing; {@link MailConfig} then refuses to start and
 *       names the variable and the allowed values.</li>
 * </ul>
 * Limit: keys are read by their canonical dotted name, so a bracketed key in a yml file (e.g. {@code "[spring.mail.host]"})
 * is not seen and the preset would still fill the default.
 */
public class MailProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "mailProviderPreset";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isEnabled(environment)) {
            return;
        }
        var provider = MailProvider.parse(environment.getProperty("app.mail.provider"));
        if (provider.isEmpty() || provider.get() == MailProvider.CUSTOM) {
            return;
        }
        var region = blankToNull(environment.getProperty("app.mail.ses-region"));
        if (region == null) {
            region = MailProviderPresets.DEFAULT_SES_REGION;
        }
        if (provider.get() == MailProvider.SES && !MailProviderPresets.isValidSesRegion(region.strip())) {
            return;
        }
        var fill = new LinkedHashMap<String, Object>();
        MailProviderPresets.defaults(provider.get(), region.strip()).forEach((key, value) -> {
            if (!StringUtils.hasText(environment.getProperty(key))) {
                fill.put(key, value);
            }
        });
        if (!fill.isEmpty()) {
            // First, but it only holds keys that were absent or blank: it cannot hide an explicit value.
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, fill));
        }
    }

    /** Same conversion as the {@code MailProperties} binding (true/yes/on/1), so MAIL_ENABLED=yes means the same everywhere. */
    private static boolean isEnabled(ConfigurableEnvironment environment) {
        try {
            return Binder.get(environment).bind("app.mail.enabled", Boolean.class).orElse(false);
        } catch (RuntimeException ex) {
            // An unparsable value: MailProperties fails the binding with its own message; nothing to preset here.
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
