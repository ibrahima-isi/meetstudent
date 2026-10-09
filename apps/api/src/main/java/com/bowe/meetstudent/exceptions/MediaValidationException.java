package com.bowe.meetstudent.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * An uploaded file failed validation. Carries the client-facing HTTP status
 * (400 invalid request, 413 too large, 415 unsupported type).
 * Extends {@link IllegalArgumentException} so existing callers keep working, but
 * {@code GlobalExceptionHandler} maps only this type to a 4xx.
 */
@Getter
public class MediaValidationException extends IllegalArgumentException {

    private final HttpStatus status;

    public MediaValidationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
