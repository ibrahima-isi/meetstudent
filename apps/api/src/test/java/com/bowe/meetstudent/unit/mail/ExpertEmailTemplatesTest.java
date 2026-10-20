package com.bowe.meetstudent.unit.mail;

import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.ExpertEmailRequested;
import com.bowe.meetstudent.mail.ExpertEmailTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertEmailTemplatesTest {

    private static ExpertEmailRequested event(EmailType type, String name, String lang, String reason) {
        return new ExpertEmailRequested(type, 3, "a@example.com", name, lang, reason);
    }

    @Test
    void theHtmlEscapesNameAndReason() {
        var mail = ExpertEmailTemplates.build(event(EmailType.EXPERT_REJECTED, "<b>Awa</b>", "en", "<img src=x onerror=1>"));
        assertThat(mail.html()).doesNotContain("<img").doesNotContain("<b>").contains("&lt;img").contains("&lt;b&gt;");
    }

    @Test
    void aFirstNameWithLineBreaksInjectsNoLineInTheTextPart() {
        var mail = ExpertEmailTemplates.build(event(EmailType.EXPERT_APPROVED, "Awa\r\n\r\nhttps://evil.example/login", "en", null));
        assertThat(mail.text().lines().filter(l -> l.contains("evil.example"))).hasSize(1);
        assertThat(mail.text().lines().toList().get(0)).isEqualTo("Hello Awa    https://evil.example/login,");
    }

    @Test
    void requestReceivedInFrench() {
        var mail = ExpertEmailTemplates.build(event(EmailType.EXPERT_REQUEST_RECEIVED, "Awa", "fr", null));
        assertThat(mail.subject()).isEqualTo("Votre demande d'enseignant MeetStudent est reçue");
        assertThat(mail.text()).contains("Bonjour Awa,").contains("en attente de validation");
    }

    @Test
    void rejectionInFrenchLabelsTheReasonMotif() {
        var mail = ExpertEmailTemplates.build(event(EmailType.EXPERT_REJECTED, "Awa", "fr", "Diplôme illisible"));
        assertThat(mail.text()).contains("Motif : Diplôme illisible");
        assertThat(mail.html()).contains("Motif : <q>Dipl&ocirc;me illisible</q>");
    }

    @Test
    void anUnsupportedLanguageFallsBackToFrench() {
        var mail = ExpertEmailTemplates.build(event(EmailType.EXPERT_APPROVED, "Awa", "de", null));
        assertThat(mail.text()).startsWith("Bonjour Awa,");
    }
}
