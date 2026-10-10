package com.bowe.meetstudent.exceptions;

import lombok.Getter;

/** A per-account limit was hit. Mapped to HTTP 429 with a {@code Retry-After} header. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
