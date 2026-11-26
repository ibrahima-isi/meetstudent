package com.bowe.meetstudent.exceptions;

/**
 * Thrown when media or media-reference mutations are temporarily paused for storage cutover or rollback.
 * Mapped to HTTP 503 Service Unavailable with a {@code Retry-After} header.
 */
public class MediaWritesPausedException extends RuntimeException {

    private final long retryAfterSeconds;

    public MediaWritesPausedException(long retryAfterSeconds) {
        super("Media writes are temporarily paused for storage maintenance");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
