package com.bowe.meetstudent.mail;

import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * French and English account emails. Only the first name is interpolated (escaped in HTML); the link is
 * built from the configured front-end origin, the whitelisted language and the token, with the token in
 * the URL fragment so it never reaches a server log or a Referer header.
 */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static EmailMessage build(AccountEmailRequested event, String frontendBaseUrl, Duration ttl) {
        var lang = normalizeLang(event.lang());
        var english = "en".equals(lang);
        var verification = event.type() == EmailType.EMAIL_VERIFICATION;
        var path = verification ? "verify-email" : "reset-password";
        var link = stripTrailingSlash(frontendBaseUrl) + "/" + lang + "/" + path + "#token=" + event.token();
        var name = event.firstName() == null ? "" : event.firstName().strip();
        var validity = formatTtl(ttl, english);

        var subject = verification
                ? (english ? "Confirm your MeetStudent email address" : "Confirmez votre adresse e-mail MeetStudent")
                : (english ? "Reset your MeetStudent password" : "Réinitialisez votre mot de passe MeetStudent");

        var text = english
                ? (verification ? EN_VERIFY_TEXT : EN_RESET_TEXT).formatted(greeting(name, true, false), validity, link)
                : (verification ? FR_VERIFY_TEXT : FR_RESET_TEXT).formatted(greeting(name, false, false), validity, link);
        var html = english
                ? (verification ? EN_VERIFY_HTML : EN_RESET_HTML).formatted(greeting(name, true, true), validity,
                        HtmlUtils.htmlEscape(link), HtmlUtils.htmlEscape(link))
                : (verification ? FR_VERIFY_HTML : FR_RESET_HTML).formatted(greeting(name, false, true), validity,
                        HtmlUtils.htmlEscape(link), HtmlUtils.htmlEscape(link));

        return new EmailMessage(event.type(), event.userId(), event.to(), subject, text, html);
    }

    private static String normalizeLang(String lang) {
        return lang != null && "en".equals(lang.strip().toLowerCase(Locale.ROOT)) ? "en" : "fr";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String greeting(String name, boolean english, boolean html) {
        var hello = english ? "Hello" : "Bonjour";
        if (name.isEmpty()) {
            return hello + ",";
        }
        return hello + " " + (html ? HtmlUtils.htmlEscape(name) : name) + ",";
    }

    private static String formatTtl(Duration ttl, boolean english) {
        long minutes = Math.max(1, ttl.toMinutes());
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return english ? hours + (hours == 1 ? " hour" : " hours") : hours + (hours == 1 ? " heure" : " heures");
        }
        return minutes + (english ? (minutes == 1 ? " minute" : " minutes") : (minutes == 1 ? " minute" : " minutes"));
    }

    // Placeholders, in order: greeting, validity, link (plain text) / greeting, validity, href, visible link (HTML).

    private static final String FR_VERIFY_TEXT = """
            %s

            Bienvenue sur MeetStudent. Confirmez votre adresse e-mail en ouvrant ce lien (valable %s) :

            %s

            Si vous n'êtes pas à l'origine de cette inscription, ignorez simplement ce message.

            L'équipe MeetStudent
            """;

    private static final String EN_VERIFY_TEXT = """
            %s

            Welcome to MeetStudent. Confirm your email address by opening this link (valid for %s):

            %s

            If you did not create this account, you can safely ignore this message.

            The MeetStudent team
            """;

    private static final String FR_RESET_TEXT = """
            %s

            Vous avez demandé à réinitialiser votre mot de passe MeetStudent. Ouvrez ce lien pour en choisir un nouveau (valable %s) :

            %s

            Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe actuel reste inchangé.

            L'équipe MeetStudent
            """;

    private static final String EN_RESET_TEXT = """
            %s

            You asked to reset your MeetStudent password. Open this link to choose a new one (valid for %s):

            %s

            If you did not ask for this, ignore this message: your current password stays unchanged.

            The MeetStudent team
            """;

    private static final String FR_VERIFY_HTML = """
            <!doctype html>
            <html lang="fr"><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2937;line-height:1.5">
            <p>%s</p>
            <p>Bienvenue sur MeetStudent. Confirmez votre adresse e-mail (lien valable %s) :</p>
            <p><a href="%s">Confirmer mon adresse e-mail</a></p>
            <p style="font-size:12px;color:#6b7280">Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :<br>%s</p>
            <p>Si vous n'êtes pas à l'origine de cette inscription, ignorez simplement ce message.</p>
            <p>L'équipe MeetStudent</p>
            </body></html>
            """;

    private static final String EN_VERIFY_HTML = """
            <!doctype html>
            <html lang="en"><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2937;line-height:1.5">
            <p>%s</p>
            <p>Welcome to MeetStudent. Confirm your email address (link valid for %s):</p>
            <p><a href="%s">Confirm my email address</a></p>
            <p style="font-size:12px;color:#6b7280">If the link does not work, copy it into your browser:<br>%s</p>
            <p>If you did not create this account, you can safely ignore this message.</p>
            <p>The MeetStudent team</p>
            </body></html>
            """;

    private static final String FR_RESET_HTML = """
            <!doctype html>
            <html lang="fr"><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2937;line-height:1.5">
            <p>%s</p>
            <p>Vous avez demandé à réinitialiser votre mot de passe MeetStudent (lien valable %s) :</p>
            <p><a href="%s">Choisir un nouveau mot de passe</a></p>
            <p style="font-size:12px;color:#6b7280">Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :<br>%s</p>
            <p>Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe actuel reste inchangé.</p>
            <p>L'équipe MeetStudent</p>
            </body></html>
            """;

    private static final String EN_RESET_HTML = """
            <!doctype html>
            <html lang="en"><body style="font-family:Arial,Helvetica,sans-serif;color:#1f2937;line-height:1.5">
            <p>%s</p>
            <p>You asked to reset your MeetStudent password (link valid for %s):</p>
            <p><a href="%s">Choose a new password</a></p>
            <p style="font-size:12px;color:#6b7280">If the link does not work, copy it into your browser:<br>%s</p>
            <p>If you did not ask for this, ignore this message: your current password stays unchanged.</p>
            <p>The MeetStudent team</p>
            </body></html>
            """;
}
