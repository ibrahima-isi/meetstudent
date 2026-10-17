package com.bowe.meetstudent.mail;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * Builds the internal email sent to the team for a contact message. The visitor's text only ever lands in the
 * body (HTML-escaped in the HTML part); the subject is flattened to one line (control characters, line breaks
 * included, become spaces) and the visitor's address is used as Reply-To only when it is a plain mailbox.
 */
public final class ContactEmailTemplates {

    private static final String SUBJECT_PREFIX = "[Contact] ";
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}+");
    private static final Pattern PLAIN_MAILBOX = Pattern.compile("[^\\s<>,;:\"()\\[\\]\\\\]+@[^\\s<>,;:\"()\\[\\]\\\\@]+");

    private ContactEmailTemplates() {
    }

    public static EmailMessage build(ContactMessageReceived event, String recipient) {
        var subject = oneLine(event.subject());
        var sender = event.senderEmail() == null ? "" : event.senderEmail().strip();
        var language = event.lang() == null ? "-" : event.lang();
        var text = """
                Message reçu via le formulaire de contact MeetStudent.

                De : %s
                Langue : %s
                Sujet : %s

                %s
                """.formatted(oneLine(sender), language, subject, event.message());
        var html = """
                <p>Message reçu via le formulaire de contact MeetStudent.</p>
                <p><strong>De :</strong> %s<br><strong>Langue :</strong> %s<br><strong>Sujet :</strong> %s</p>
                <p>%s</p>
                """.formatted(HtmlUtils.htmlEscape(oneLine(sender)), HtmlUtils.htmlEscape(language), HtmlUtils.htmlEscape(subject),
                HtmlUtils.htmlEscape(event.message()).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>"));
        var replyTo = PLAIN_MAILBOX.matcher(sender).matches() ? sender : null;
        return new EmailMessage(EmailType.CONTACT_MESSAGE, null, recipient, SUBJECT_PREFIX + subject, text, html, replyTo);
    }

    private static String oneLine(String value) {
        return value == null ? "" : CONTROL.matcher(value).replaceAll(" ").strip();
    }
}
