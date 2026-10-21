package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.config.ForumProperties;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.services.ForumRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForumRateLimiterTest {

    private final ForumProperties properties = new ForumProperties();
    private final ContactRateLimiterTest.MutableClock clock = new ContactRateLimiterTest.MutableClock(Instant.parse("2026-10-20T10:00:00Z"));
    private final ForumRateLimiter limiter = new ForumRateLimiter(properties, clock);

    @Test
    void allowsFiveThenRefusesWithTheTimeLeft() {
        for (int i = 0; i < 5; i++) {
            limiter.check(7);
            clock.advance(Duration.ofSeconds(5));
        }
        // The first hit was 25 s ago: its slot frees in 35 s.
        assertThatThrownBy(() -> limiter.check(7))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(35));
    }

    @Test
    void usersAreIndependentAndTheWindowSlides() {
        for (int i = 0; i < 5; i++) {
            limiter.check(7);
        }
        assertThatCode(() -> limiter.check(8)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(7)).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(60));
        assertThatCode(() -> limiter.check(7)).doesNotThrowAnyException();
    }

    @Test
    void trackedUsersAreBounded() {
        properties.getRateLimit().setMaxTrackedUsers(50);
        for (int i = 0; i < 500; i++) {
            limiter.check(i);
        }
        assertThat(limiter.trackedUsers()).isLessThanOrEqualTo(50);
    }
}
