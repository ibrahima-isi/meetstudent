package com.bowe.meetstudent.services;

import com.bowe.meetstudent.config.ForumProperties;
import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sliding-window limits on forum actions per user, kept in memory (one API instance), like {@link ContactRateLimiter}.
 * Two independent budgets: <b>writes</b> (thread and reply creations and edits share one, deletes are free; 5 per
 * minute by default) and <b>reports</b> (10 per hour by default, never shared with the writes one). Memory is bounded:
 * each user holds at most {@code maxPerWindow} timestamps and at most {@code maxTrackedUsers} users are remembered
 * per budget (least recently seen dropped first); expired users are also swept about once a minute. Refused
 * attempts are not recorded.
 */
@Component
public class ForumRateLimiter {

    private final Window writes;
    private final Window reports;

    public ForumRateLimiter(ForumProperties properties, Clock clock) {
        this.writes = new Window(properties::getRateLimit, clock, "Trop de messages envoyés, réessayez dans un instant");
        this.reports = new Window(properties::getReportRateLimit, clock, "Trop de signalements envoyés, réessayez plus tard");
    }

    /** Records one message for the user, or throws {@link TooManyRequestsException} when the budget is spent. */
    public void check(int userId) {
        writes.check(userId);
    }

    /** Records one report for the user, or throws {@link TooManyRequestsException} when the (separate) budget is spent. */
    public void checkReport(int userId) {
        reports.check(userId);
    }

    public int trackedUsers() {
        return writes.trackedUsers();
    }

    private static final class Window {

        private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

        private final Supplier<ForumProperties.RateLimit> config;
        private final Clock clock;
        private final String message;
        private final Map<Integer, Deque<Instant>> hits;
        private Instant lastSweep;

        Window(Supplier<ForumProperties.RateLimit> config, Clock clock, String message) {
            this.config = config;
            this.clock = clock;
            this.message = message;
            this.lastSweep = clock.instant();
            this.hits = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Deque<Instant>> eldest) {
                    return size() > Math.max(1, Window.this.config.get().getMaxTrackedUsers());
                }
            };
        }

        synchronized void check(int userId) {
            var limit = config.get();
            var now = clock.instant();
            var window = Duration.ofSeconds(limit.getWindowSeconds());
            sweepIfDue(now, window);

            var times = hits.computeIfAbsent(userId, key -> new ArrayDeque<>());
            while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) {
                times.pollFirst();
            }
            if (times.size() >= limit.getMaxPerWindow()) {
                var wait = Duration.between(now, times.peekFirst().plus(window));
                throw new TooManyRequestsException(message, (wait.toMillis() + 999) / 1000);
            }
            times.addLast(now);
        }

        synchronized int trackedUsers() {
            return hits.size();
        }

        private void sweepIfDue(Instant now, Duration window) {
            if (Duration.between(lastSweep, now).compareTo(SWEEP_EVERY) < 0) {
                return;
            }
            lastSweep = now;
            for (Iterator<Deque<Instant>> it = hits.values().iterator(); it.hasNext(); ) {
                var times = it.next();
                if (times.isEmpty() || !times.peekLast().plus(window).isAfter(now)) {
                    it.remove();
                }
            }
        }
    }
}
