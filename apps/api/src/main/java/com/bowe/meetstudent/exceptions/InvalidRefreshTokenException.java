package com.bowe.meetstudent.exceptions;

/**
 * Thrown when a refresh token is unknown or expired. Mapped to HTTP 401 by {@link GlobalExceptionHandler}.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
