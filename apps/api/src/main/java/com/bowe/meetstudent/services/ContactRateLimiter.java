package com.bowe.meetstudent.services;

import com.bowe.meetstudent.config.ContactProperties;
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
 * Sliding-window limit on contact messages per client IP, kept in memory (one API instance). Memory is
 * bounded twice: each client holds at most {@code maxPerWindow} timestamps, and at most
 * {@code maxTrackedClients} clients are remembered (least recently seen dropped first); expired clients are
 * also swept about once a minute. Refused attempts are not recorded, so hammering does not extend a block.
 */
@Component
public class ContactRateLimiter {

    private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

    private final ContactProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits;
    private Instant lastSweep;

    public ContactRateLimiter(ContactProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.lastSweep = clock.instant();
        this.hits = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
                return size() > Math.max(1, ContactRateLimiter.this.properties.getRateLimit().getMaxTrackedClients());
            }
        };
    }

    /** Records one attempt for the client, or throws {@link TooManyRequestsException} when its budget is spent. */
    public synchronized void check(String client) {
        var config = properties.getRateLimit();
        var now = clock.instant();
        var window = Duration.ofMinutes(config.getWindowMinutes());
        sweepIfDue(now, window);

        var times = hits.computeIfAbsent(client, key -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) {
            times.pollFirst();
        }
        if (times.size() >= config.getMaxPerWindow()) {
            var wait = Duration.between(now, times.peekFirst().plus(window));
            throw new TooManyRequestsException("Trop de messages envoyés, réessayez plus tard", (wait.toMillis() + 999) / 1000);
        }
        times.addLast(now);
    }

    public synchronized int trackedClients() {
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
