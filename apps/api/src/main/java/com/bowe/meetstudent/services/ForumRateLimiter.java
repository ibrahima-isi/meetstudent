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

/**
 * Sliding-window limit on forum writes per user (thread and reply creations and edits share one budget; deletes are free), kept in memory
 * (one API instance), like {@link ContactRateLimiter}. Memory is bounded: each user holds at most
 * {@code maxPerWindow} timestamps and at most {@code maxTrackedUsers} users are remembered (least recently
 * seen dropped first); expired users are also swept about once a minute. Refused attempts are not recorded.
 */
@Component
public class ForumRateLimiter {

    private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

    private final ForumProperties properties;
    private final Clock clock;
    private final Map<Integer, Deque<Instant>> hits;
    private Instant lastSweep;

    public ForumRateLimiter(ForumProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.lastSweep = clock.instant();
        this.hits = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Deque<Instant>> eldest) {
                return size() > Math.max(1, ForumRateLimiter.this.properties.getRateLimit().getMaxTrackedUsers());
            }
        };
    }

    /** Records one message for the user, or throws {@link TooManyRequestsException} when the budget is spent. */
    public synchronized void check(int userId) {
        var config = properties.getRateLimit();
        var now = clock.instant();
        var window = Duration.ofSeconds(config.getWindowSeconds());
        sweepIfDue(now, window);

        var times = hits.computeIfAbsent(userId, key -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) {
            times.pollFirst();
        }
        if (times.size() >= config.getMaxPerWindow()) {
            var wait = Duration.between(now, times.peekFirst().plus(window));
            throw new TooManyRequestsException("Trop de messages envoyés, réessayez dans un instant", (wait.toMillis() + 999) / 1000);
        }
        times.addLast(now);
    }

    public synchronized int trackedUsers() {
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
