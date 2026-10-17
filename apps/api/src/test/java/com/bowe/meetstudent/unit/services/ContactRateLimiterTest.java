package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.config.ContactProperties;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.services.ContactRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactRateLimiterTest {

    private final ContactProperties properties = new ContactProperties();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
    private final ContactRateLimiter limiter = new ContactRateLimiter(properties, clock);

    @Test
    void allowsTheConfiguredNumberThenRefusesWithTheTimeLeft() {
        for (int i = 0; i < 5; i++) {
            limiter.check("203.0.113.1");
            clock.advance(Duration.ofMinutes(1));
        }
        // The first hit was 5 minutes ago, so its slot frees in 55 minutes.
        assertThatThrownBy(() -> limiter.check("203.0.113.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(Duration.ofMinutes(55).toSeconds()));
    }

    @Test
    void aRefusedAttemptDoesNotExtendTheBlock() {
        for (int i = 0; i < 5; i++) {
            limiter.check("203.0.113.1");
        }
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> limiter.check("203.0.113.1")).isInstanceOf(TooManyRequestsException.class);
        }
        clock.advance(Duration.ofMinutes(60));
        assertThatCode(() -> limiter.check("203.0.113.1")).doesNotThrowAnyException();
    }

    @Test
    void clientsAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.check("203.0.113.1");
        }
        assertThatCode(() -> limiter.check("203.0.113.2")).doesNotThrowAnyException();
    }

    @Test
    void theWindowSlides() {
        for (int i = 0; i < 5; i++) {
            limiter.check("203.0.113.1");
        }
        clock.advance(Duration.ofMinutes(59));
        assertThatThrownBy(() -> limiter.check("203.0.113.1")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.check("203.0.113.1")).doesNotThrowAnyException();
    }

    @Test
    void trackedClientsAreBounded() {
        properties.getRateLimit().setMaxTrackedClients(100);
        for (int i = 0; i < 1000; i++) {
            limiter.check("10.0." + (i / 250) + "." + (i % 250));
        }
        assertThat(limiter.trackedClients()).isLessThanOrEqualTo(100);
    }

    @Test
    void expiredClientsAreForgotten() {
        for (int i = 0; i < 50; i++) {
            limiter.check("10.1.0." + i);
        }
        clock.advance(Duration.ofMinutes(61));
        limiter.check("10.2.0.1");
        assertThat(limiter.trackedClients()).isEqualTo(1);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
