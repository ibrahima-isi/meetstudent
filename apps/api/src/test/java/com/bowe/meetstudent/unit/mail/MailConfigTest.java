package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.mail.DevLogEmailSender;
import com.bowe.meetstudent.mail.DisabledEmailSender;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.mail.MailConfig;
import com.bowe.meetstudent.mail.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailConfig.class);

    private static final String[] ENABLED = {
            "app.mail.enabled=true",
            "app.mail.from=no-reply@example.com",
            "spring.mail.host=smtp.invalid.example",
            "app.frontend-base-url=https://app.example.com"
    };

    @Test
    void defaultsToTheDisabledSender() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EmailSender.class)).isInstanceOf(DisabledEmailSender.class);
            assertThat(ctx.getBean(EmailSender.class).isEnabled()).isFalse();
        });
    }

    @Test
    void enabledAndFullyConfiguredUsesSmtp() {
        runner.withPropertyValues(ENABLED).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class);
            assertThat(ctx.getBean(EmailSender.class).isEnabled()).isTrue();
        });
    }

    @Test
    void enabledWithoutSmtpHostFailsAtBootNamingTheVariable() {
        runner.withPropertyValues("app.mail.enabled=true", "app.mail.from=no-reply@example.com",
                "app.frontend-base-url=https://app.example.com").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SPRING_MAIL_HOST");
        });
    }

    @Test
    void enabledWithBlankSmtpHostFailsAtBoot() {
        runner.withPropertyValues("app.mail.enabled=true", "app.mail.from=no-reply@example.com",
                "app.frontend-base-url=https://app.example.com", "spring.mail.host=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SPRING_MAIL_HOST");
        });
    }

    @Test
    void enabledWithBlankFromFailsAtBoot() {
        runner.withPropertyValues(ENABLED).withPropertyValues("app.mail.from=  ").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MAIL_FROM");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://app.example.com/some/path",
            "javascript:alert(1)",
            "https://app.example.com/#frag",
            "https://app.example.com?x=1",
            "ftp://app.example.com",
            "https://evil@app.example.com",
            "https://user:pw@app.example.com",
            "app.example.com",
            ""
    })
    void enabledWithAnInvalidFrontendBaseUrlFailsAtBoot(String baseUrl) {
        runner.withPropertyValues(ENABLED).withPropertyValues("app.frontend-base-url=" + baseUrl).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FRONTEND_BASE_URL");
        });
    }

    @Test
    void aTrailingSlashOnlyIsAccepted() {
        runner.withPropertyValues(ENABLED).withPropertyValues("app.frontend-base-url=https://app.example.com/")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void productionRefusesAnHttpFrontendBaseUrl() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(ENABLED).withPropertyValues("app.frontend-base-url=http://app.example.com")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("https");
                });
    }

    @Test
    void productionAcceptsAnHttpsFrontendBaseUrl() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(ENABLED)
                .run(ctx -> assertThat(ctx.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class));
    }

    @Test
    void productionRefusesDevLog() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("app.mail.dev-log=true")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("dev-log");
                });
    }

    @Test
    void devLogOutsideProductionUsesTheDevLogSender() {
        runner.withPropertyValues("app.mail.dev-log=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EmailSender.class)).isInstanceOf(DevLogEmailSender.class);
        });
    }

    @Test
    void exposesAClockAndTheMailExecutor() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(java.time.Clock.class);
            assertThat(ctx).hasBean("mailExecutor");
        });
    }

    @Test
    void asyncOffGivesASynchronousExecutor() {
        runner.withPropertyValues("app.mail.async=false").run(ctx -> {
            var executor = ctx.getBean("mailExecutor", java.util.concurrent.Executor.class);
            var caller = Thread.currentThread();
            var ranOn = new java.util.concurrent.atomic.AtomicReference<Thread>();
            executor.execute(() -> ranOn.set(Thread.currentThread()));
            assertThat(ranOn.get()).isSameAs(caller);
        });
    }

    @Test
    void aFullQueueAndAShutdownPoolLogDifferentMessages() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MailConfig.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            runner.run(ctx -> {
                var executor = ctx.getBean("mailExecutor", org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
                var pool = executor.getThreadPoolExecutor();
                pool.shutdown();
                executor.execute(() -> { });
                assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("shutting down"));
                assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("queue is full"));
            });
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void theFrontendBaseUrlIsStrippedOnceSoValidationAndLinksAgree() {
        var props = new com.bowe.meetstudent.mail.MailProperties();
        props.setFrontendBaseUrl("  https://app.example.com ");
        assertThat(props.getFrontendBaseUrl()).isEqualTo("https://app.example.com");
    }
}
