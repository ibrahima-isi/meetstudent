package com.bowe.meetstudent.mail;

/**
 * Result of one delivery attempt. Carries no detail on purpose: never an address, a host, a credential or an
 * exception message, so it can be logged, stored and returned to an admin as is.
 */
public enum SendOutcome {
    SENT,
    /** Mail is switched off (MAIL_ENABLED=false): nothing was attempted. */
    DISABLED,
    AUTH_FAILED,
    CONNECTION_FAILED,
    TLS_FAILED,
    /** The server or the address check refused the recipient/sender. */
    REJECTED,
    FAILED;

    /** Worth another attempt: the cause may be gone in a few seconds. */
    public boolean isTransient() {
        return this == CONNECTION_FAILED || this == TLS_FAILED || this == FAILED;
    }
}
