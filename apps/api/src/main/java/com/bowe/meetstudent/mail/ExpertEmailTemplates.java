package com.bowe.meetstudent.mail;

import org.springframework.web.util.HtmlUtils;

import java.util.Locale;

/**
 * French and English mails of the Expert approval flow. Only the first name and the rejection reason are
 * interpolated, escaped in the HTML part; the subject is fixed, so neither can inject a header. The text part keeps
 * the reason as typed (carriage returns dropped): it is a body, not a header.
 */
public final class ExpertEmailTemplates {

    private ExpertEmailTemplates() {
    }

    public static EmailMessage build(ExpertEmailRequested event) {
        var english = event.lang() != null && "en".equals(event.lang().strip().toLowerCase(Locale.ROOT));
        var type = event.type();
        // Line breaks would let a first name start a new line (a fake link) in the text part.
        var name = event.firstName() == null ? "" : event.firstName().replaceAll("[\\r\\n]", " ").strip();
        var reason = event.reason() == null || event.reason().isBlank() ? null : event.reason().replace("\r", "");

        String subject;
        String body;
        String reasonLabel = english ? "Reason: " : "Motif : ";
        switch (type) {
            case EXPERT_REQUEST_RECEIVED -> {
                subject = english ? "We received your MeetStudent teacher request" : "Votre demande d'enseignant MeetStudent est reçue";
                body = english
                        ? "Thank you for joining MeetStudent. Your teacher account is pending approval by our team. You can already sign in and browse the site like a student; you will be able to rate programs and courses once the account is approved. We will email you as soon as a decision is made."
                        : "Merci d'avoir rejoint MeetStudent. Votre compte enseignant est en attente de validation par notre équipe. Vous pouvez déjà vous connecter et consulter le site comme un étudiant ; vous pourrez noter les formations et les cours une fois le compte approuvé. Nous vous écrirons dès qu'une décision sera prise.";
            }
            case EXPERT_APPROVED -> {
                subject = english ? "Your MeetStudent teacher account is approved" : "Votre compte enseignant MeetStudent est approuvé";
                body = english
                        ? "Good news: your teacher account has been approved. You can now rate programs and courses as an expert."
                        : "Bonne nouvelle : votre compte enseignant a été approuvé. Vous pouvez désormais noter les formations et les cours en tant qu'expert.";
            }
            case EXPERT_REJECTED -> {
                subject = english ? "Your MeetStudent teacher request was declined" : "Votre demande d'enseignant MeetStudent a été refusée";
                body = english
                        ? "We are sorry: your teacher account request was not approved. You can keep using MeetStudent like a student."
                        : "Nous sommes désolés : votre demande de compte enseignant n'a pas été approuvée. Vous pouvez continuer à utiliser MeetStudent comme un étudiant.";
            }
            default -> throw new IllegalArgumentException("Not an Expert approval email");
        }
        var hello = english ? "Hello" : "Bonjour";
        var signature = english ? "The MeetStudent team" : "L'équipe MeetStudent";
        var showReason = type == EmailType.EXPERT_REJECTED && reason != null;

        var text = hello + (name.isEmpty() ? "" : " " + name) + ",\n\n" + body + "\n\n"
                + (showReason ? reasonLabel + reason + "\n\n" : "") + signature + "\n";
        var html = "<!doctype html>\n<html lang=\"" + (english ? "en" : "fr")
                + "\"><body style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2937;line-height:1.5\">\n"
                + "<p>" + hello + (name.isEmpty() ? "" : " " + HtmlUtils.htmlEscape(name)) + ",</p>\n"
                + "<p>" + HtmlUtils.htmlEscape(body) + "</p>\n"
                + (showReason ? "<p>" + reasonLabel + "<q>" + HtmlUtils.htmlEscape(reason).replace("\n", "<br>") + "</q></p>\n" : "")
                + "<p>" + HtmlUtils.htmlEscape(signature) + "</p>\n</body></html>\n";
        return new EmailMessage(type, event.userId(), event.to(), subject, text, html);
    }
}
