package com.bowe.meetstudent.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Locale;

/**
 * Picks the {@link EmailSender} and fails fast on an inconsistent mail configuration. Nothing is
 * validated while mail is disabled, so a default deployment boots without any SMTP setting.
 * Error messages name the environment variables and never echo secrets.
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    public EmailSender emailSender(MailProperties properties, Environment environment, ObjectProvider<JavaMailSender> mailSender) {
        var mail = properties.getMail();
        var prod = environment.acceptsProfiles(Profiles.of("prod"));

        if (mail.isDevLog() && prod) {
            throw new IllegalStateException("app.mail.dev-log writes links to the log and must not be enabled under the prod profile");
        }
        if (mail.isEnabled()) {
            // The provider preset has already filled spring.mail.* by now (MailProviderEnvironmentPostProcessor);
            // here only a value it could not use is reported, naming the variable and never echoing the value.
            var provider = MailProvider.parse(mail.getProvider())
                    .orElseThrow(() -> new IllegalStateException("MAIL_PROVIDER must be one of: " + MailProvider.ALLOWED));
            if (provider == MailProvider.SES && !MailProviderPresets.isValidSesRegion(sesRegion(mail.getSesRegion()))) {
                throw new IllegalStateException("MAIL_SES_REGION must be an AWS region such as eu-west-1");
            }
            var host = environment.getProperty("spring.mail.host");
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("MAIL_ENABLED=true requires SPRING_MAIL_HOST (or MAIL_PROVIDER=brevo, gmail or ses)");
            }
            if (mail.getFrom() == null || mail.getFrom().isBlank()) {
                throw new IllegalStateException("MAIL_ENABLED=true requires MAIL_FROM");
            }
            validateFrontendBaseUrl(properties.getFrontendBaseUrl(), prod);
            var sender = mailSender.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException("MAIL_ENABLED=true requires SPRING_MAIL_HOST (no JavaMailSender configured)");
            }
            log.info("Email sending is enabled (provider preset: {})", provider.name().toLowerCase(Locale.ROOT));
            return new SmtpEmailSender(sender, mail.getFrom().strip());
        }
        if (mail.isDevLog()) {
            validateFrontendBaseUrl(properties.getFrontendBaseUrl(), false);
            log.warn("Email dev-log is ON: emails, links included, are written to the log (local use only)");
            return new DevLogEmailSender();
        }
        log.info("Email sending is disabled (set MAIL_ENABLED=true and SPRING_MAIL_HOST to enable it)");
        return new DisabledEmailSender();
    }

    private static String sesRegion(String configured) {
        return configured == null || configured.isBlank() ? MailProviderPresets.DEFAULT_SES_REGION : configured.strip();
    }

    private static void validateFrontendBaseUrl(String value, boolean requireHttps) {
        URI uri;
        try {
            uri = new URI(value == null ? "" : value.strip());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("FRONTEND_BASE_URL is not a valid URL");
        }
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        var path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!(scheme.equals("http") || scheme.equals("https"))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || !(path.isEmpty() || path.equals("/"))
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalStateException(
                    "FRONTEND_BASE_URL must be an absolute http(s) origin without path, query or fragment, e.g. https://app.example.com");
        }
        if (requireHttps && !scheme.equals("https")) {
            throw new IllegalStateException("FRONTEND_BASE_URL must use https under the prod profile");
        }
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Trap: Boot's default {@code applicationTaskExecutor} backs off as soon as any other Executor bean
     * exists, unless {@code spring.task.execution.mode=force} (set in application.yml) is configured.
     * Do not remove that property.
     * <p>
     * Bounded pool so a slow SMTP server cannot pile up threads; a full queue drops the email and says so. */
    @Bean(name = "mailExecutor")
    public TaskExecutor mailExecutor(MailProperties properties) {
        if (!properties.getMail().isAsync()) {
            return new SyncTaskExecutor();
        }
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("Mail executor is shutting down: an email was dropped");
            } else {
                log.warn("Mail queue is full: an email was dropped");
            }
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /**
     * Contact-form mails get their own small pool: a delivery can hold its thread for the whole retry backoff,
     * and the form is public, so a flood must only ever fill this queue and never delay a verification or a
     * reset mail (those use {@code mailExecutor}). A full queue drops the mail with a warning; the message row
     * then stays PENDING and an admin can resend it.
     */
    @Bean(name = "contactMailExecutor")
    public TaskExecutor contactMailExecutor(MailProperties properties) {
        if (!properties.getMail().isAsync()) {
            return new SyncTaskExecutor();
        }
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("contact-mail-");
        // Fixed size: threads beyond the core are only created once the queue is full, so core == max.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("Contact mail executor is shutting down: a contact mail was dropped (its message stays PENDING)");
            } else {
                log.warn("Contact mail queue is full: a contact mail was dropped (its message stays PENDING, resend it from the back office)");
            }
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
