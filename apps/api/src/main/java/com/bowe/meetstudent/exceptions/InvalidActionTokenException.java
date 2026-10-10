package com.bowe.meetstudent.exceptions;

/**
 * An email-verification or password-reset token is unknown, expired, already used or issued for another
 * address. One type and one body for all cases, so a caller cannot tell them apart. Mapped to HTTP 400.
 */
public class InvalidActionTokenException extends RuntimeException {
    public InvalidActionTokenException() {
        super("Invalid or expired token");
    }
}
