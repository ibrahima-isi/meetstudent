package com.bowe.meetstudent.mail;

/** Kinds of transactional emails: three for accounts, plus the internal contact-form message. */
public enum EmailType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    EMAIL_CHANGE,
    /** Sent to the team, not to a user; built by {@link ContactEmailTemplates}, never by {@link EmailTemplates}. */
    CONTACT_MESSAGE
}
