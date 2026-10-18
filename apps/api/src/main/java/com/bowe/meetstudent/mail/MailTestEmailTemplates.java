package com.bowe.meetstudent.mail;

/**
 * The SMTP self-test mail: short, bilingual (French then English), no link, no token and no data of any visitor.
 * Only ever built for the admin who asked for it.
 */
public final class MailTestEmailTemplates {

    static final String SUBJECT = "Test SMTP MeetStudent / MeetStudent SMTP test";

    private MailTestEmailTemplates() {
    }

    public static EmailMessage build(Integer adminId, String adminEmail) {
        var text = """
                [FR] Ce message confirme que l'envoi d'e-mails de MeetStudent fonctionne. Il a été demandé depuis l'espace d'administration. Aucune action n'est nécessaire.

                [EN] This message confirms that MeetStudent email delivery works. It was requested from the admin area. No action is needed.
                """;
        var html = """
                <p><strong>FR</strong> &middot; Ce message confirme que l'envoi d'e-mails de MeetStudent fonctionne. Il a été demandé depuis l'espace d'administration. Aucune action n'est nécessaire.</p>
                <p><strong>EN</strong> &middot; This message confirms that MeetStudent email delivery works. It was requested from the admin area. No action is needed.</p>
                """;
        return new EmailMessage(EmailType.MAIL_TEST, adminId, adminEmail, SUBJECT, text, html);
    }
}
