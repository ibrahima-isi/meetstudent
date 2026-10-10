package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.mail.AccountEmailRequested;
import com.bowe.meetstudent.mail.EmailMessage;
import com.bowe.meetstudent.mail.EmailTemplates;
import com.bowe.meetstudent.mail.EmailType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplatesTest {

    private static final String BASE = "https://app.example.com";
    private static final String TOKEN = "abcDEF123_-xyz";

    private EmailMessage build(EmailType type, String firstName, String lang, String to) {
        var ttl = type == EmailType.PASSWORD_RESET ? Duration.ofMinutes(60) : Duration.ofHours(24);
        return EmailTemplates.build(new AccountEmailRequested(type, 7, to, firstName, lang, TOKEN), BASE, ttl);
    }

    @Test
    void verificationLinkUsesTheFragmentNeverTheQuery() {
        var m = build(EmailType.EMAIL_VERIFICATION, "Awa", "fr", "awa@example.com");
        assertThat(m.text()).contains(BASE + "/fr/verify-email#token=" + TOKEN);
        assertThat(m.html()).contains(BASE + "/fr/verify-email#token=" + TOKEN);
        assertThat(m.text() + m.html()).doesNotContain("?token=");
        assertThat(m.type()).isEqualTo(EmailType.EMAIL_VERIFICATION);
        assertThat(m.userId()).isEqualTo(7);
        assertThat(m.to()).isEqualTo("awa@example.com");
    }

    @Test
    void resetLinkUsesTheFragmentNeverTheQuery() {
        var m = build(EmailType.PASSWORD_RESET, "Awa", "fr", "awa@example.com");
        assertThat(m.text()).contains(BASE + "/fr/reset-password#token=" + TOKEN);
        assertThat(m.text() + m.html()).doesNotContain("?token=");
    }

    @Test
    void englishVariantUsesEnglishCopyAndPath() {
        var verify = build(EmailType.EMAIL_VERIFICATION, "Awa", "en", "awa@example.com");
        var reset = build(EmailType.PASSWORD_RESET, "Awa", "en", "awa@example.com");
        var frReset = build(EmailType.PASSWORD_RESET, "Awa", "fr", "awa@example.com");
        assertThat(verify.text()).contains(BASE + "/en/verify-email#token=" + TOKEN);
        assertThat(reset.text()).contains(BASE + "/en/reset-password#token=" + TOKEN);
        assertThat(reset.subject()).isNotEqualTo(frReset.subject());
        assertThat(reset.text()).contains("Hello Awa").contains("1 hour");
        assertThat(frReset.text()).contains("Bonjour Awa").contains("1 heure");
        assertThat(verify.text()).contains("24 hours");
    }

    @Test
    void unknownOrMissingLanguageFallsBackToFrench() {
        for (String lang : new String[]{"de", null, "", "EN-us", "../x"}) {
            var m = build(EmailType.PASSWORD_RESET, "Awa", lang, "awa@example.com");
            assertThat(m.text()).contains(BASE + "/fr/reset-password#token=").contains("Bonjour");
        }
    }

    @Test
    void languageIsCaseInsensitiveForTheWhitelist() {
        var m = build(EmailType.PASSWORD_RESET, "Awa", "EN", "awa@example.com");
        assertThat(m.text()).contains(BASE + "/en/reset-password#token=");
    }

    @Test
    void hostileFirstNameIsEscapedInHtml() {
        var m = build(EmailType.EMAIL_VERIFICATION, "<img src=x onerror=alert(1)>", "fr", "awa@example.com");
        assertThat(m.html()).doesNotContain("<img src=x").contains("&lt;img src=x onerror=alert(1)&gt;");
        assertThat(m.subject()).doesNotContain("img");
    }

    @Test
    void blankFirstNameStillGreets() {
        var m = build(EmailType.EMAIL_VERIFICATION, "  ", "en", "awa@example.com");
        assertThat(m.text()).startsWith("Hello,");
    }

    @Test
    void trailingSlashOnTheBaseUrlDoesNotDoubleTheSeparator() {
        var m = EmailTemplates.build(new AccountEmailRequested(EmailType.PASSWORD_RESET, 1, "a@example.com", "A", "fr", TOKEN),
                BASE + "/", Duration.ofMinutes(60));
        assertThat(m.text()).contains(BASE + "/fr/reset-password#token=").doesNotContain(".com//");
    }

    @Test
    void recipientWithLineBreaksIsRejectedWithoutEchoingIt() {
        for (String to : new String[]{"a@example.com\r\nBcc: evil@example.com", "a@example.com\nBcc: x@y.z", "a@example.com\r"}) {
            assertThatThrownBy(() -> build(EmailType.PASSWORD_RESET, "Awa", "fr", to))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("evil@example.com");
        }
    }

    @Test
    void blankRecipientIsRejected() {
        assertThatThrownBy(() -> build(EmailType.PASSWORD_RESET, "Awa", "fr", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
