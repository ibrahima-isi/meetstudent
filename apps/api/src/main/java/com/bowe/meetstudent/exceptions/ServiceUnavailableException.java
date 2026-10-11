package com.bowe.meetstudent.exceptions;

/** A required capability (e.g. outgoing email) is not available. Mapped to HTTP 503. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
