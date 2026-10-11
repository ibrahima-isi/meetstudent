package com.bowe.meetstudent.exceptions;

/** The email address already belongs to another account (compared case-insensitively). Mapped to 400 {"email": ...}. */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("Cet email est déjà utilisé");
    }
}
