package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.mail.MailProvider;
import com.bowe.meetstudent.mail.MailProviderPresets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailProviderPresetsTest {

    private static final String AUTH = "spring.mail.properties.mail.smtp.auth";
    private static final String STARTTLS = "spring.mail.properties.mail.smtp.starttls.enable";
    private static final String STARTTLS_REQUIRED = "spring.mail.properties.mail.smtp.starttls.required";

    private static void assertSubmissionWithMandatoryStartTls(Map<String, String> defaults) {
        assertThat(defaults).containsEntry("spring.mail.port", "587")
                .containsEntry(AUTH, "true")
                .containsEntry(STARTTLS, "true")
                .containsEntry(STARTTLS_REQUIRED, "true");
    }

    @Test
    void brevoPreset() {
        var defaults = MailProviderPresets.defaults(MailProvider.BREVO, "eu-west-1");
        assertThat(defaults).containsEntry("spring.mail.host", "smtp-relay.brevo.com");
        assertSubmissionWithMandatoryStartTls(defaults);
    }

    @Test
    void gmailPreset() {
        var defaults = MailProviderPresets.defaults(MailProvider.GMAIL, "eu-west-1");
        assertThat(defaults).containsEntry("spring.mail.host", "smtp.gmail.com");
        assertSubmissionWithMandatoryStartTls(defaults);
    }

    @Test
    void sesPresetUsesTheRegionInTheHost() {
        var defaults = MailProviderPresets.defaults(MailProvider.SES, "eu-west-1");
        assertThat(defaults).containsEntry("spring.mail.host", "email-smtp.eu-west-1.amazonaws.com");
        assertSubmissionWithMandatoryStartTls(defaults);
        assertThat(MailProviderPresets.defaults(MailProvider.SES, "us-east-1"))
                .containsEntry("spring.mail.host", "email-smtp.us-east-1.amazonaws.com");
    }

    @Test
    void customHasNoDefaultsAtAll() {
        assertThat(MailProviderPresets.defaults(MailProvider.CUSTOM, "eu-west-1")).isEmpty();
    }

    @Test
    void presetsNeverCarryCredentials() {
        for (var provider : MailProvider.values()) {
            assertThat(MailProviderPresets.defaults(provider, "eu-west-1").keySet())
                    .noneMatch(k -> k.endsWith(".username") || k.endsWith(".password"));
        }
    }

    @Test
    void parseIsCaseInsensitiveTrimsAndTreatsBlankOrNullAsCustom() {
        assertThat(MailProvider.parse("brevo")).contains(MailProvider.BREVO);
        assertThat(MailProvider.parse("  Gmail ")).contains(MailProvider.GMAIL);
        assertThat(MailProvider.parse("SES")).contains(MailProvider.SES);
        assertThat(MailProvider.parse("custom")).contains(MailProvider.CUSTOM);
        assertThat(MailProvider.parse("")).contains(MailProvider.CUSTOM);
        assertThat(MailProvider.parse("   ")).contains(MailProvider.CUSTOM);
        assertThat(MailProvider.parse(null)).contains(MailProvider.CUSTOM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sendgrid", "mailgun", "smtp", "brevo,gmail", "ses ses"})
    void parseRejectsUnknownProviders(String value) {
        assertThat(MailProvider.parse(value)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"eu-west-1", "us-east-1", "ap-southeast-2", "us-gov-west-1", "eu-central-2"})
    void validRegions(String region) {
        assertThat(MailProviderPresets.isValidSesRegion(region)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "EU-WEST-1", "eu_west_1", "evil.com/x", "eu-west-1.attacker.net", "a b", "-x", "x-"})
    void invalidRegionsCannotBuildAHostname(String region) {
        assertThat(MailProviderPresets.isValidSesRegion(region)).isFalse();
    }
}
