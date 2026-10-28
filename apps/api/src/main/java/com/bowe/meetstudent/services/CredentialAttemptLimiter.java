package com.bowe.meetstudent.services;

import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Progressive lockout against password guessing, kept in memory (one API instance, reset on restart, like
 * {@link ForumRateLimiter}). Two separate namespaces: <b>sign-in</b>, keyed by the lower-cased email whether an
 * account exists or not (stored as a SHA-256, so the size of a key is fixed and no address is kept), and
 * <b>current password</b> checks (email change, account deletion, password change), keyed by user id.
 * <p>
 * After {@value #MAX_FAILURES} consecutive failures the key is locked for 1 minute, after the next streak 5 minutes,
 * then 15 minutes (cap); a success resets everything, and a key with no failure for a day is forgotten. While locked
 * the password is not checked at all and {@link TooManyRequestsException} (429 + {@code Retry-After}) is thrown.
 * Attempts still running count against the streak, so parallel guesses cannot exceed it. Errors that are not a wrong
 * password (database down...) do not count. Memory is bounded: at most {@code maxTrackedKeys} keys per namespace
 * (least recently seen dropped first) and finished entries are swept about once a minute.
 */
@Component
public class CredentialAttemptLimiter {

    public static final int MAX_FAILURES = 5;
    static final List<Duration> LOCKS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
    static final Duration FORGET_AFTER = Duration.ofHours(24);
    static final int DEFAULT_MAX_TRACKED_KEYS = 50_000;
    static final String MESSAGE = "Trop de tentatives, réessayez plus tard";

    private final Attempts<String> logins;
    private final Attempts<Integer> currentPasswords;

    @Autowired
    public CredentialAttemptLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_TRACKED_KEYS);
    }

    public CredentialAttemptLimiter(Clock clock, int maxTrackedKeys) {
        this.logins = new Attempts<>(clock, maxTrackedKeys);
        this.currentPasswords = new Attempts<>(clock, maxTrackedKeys);
    }

    /**
     * Runs one sign-in attempt for {@code email}: an {@link AuthenticationException} counts as a failure, a returned
     * authentication as a success.
     *
     * @throws TooManyRequestsException the email is locked (the attempt is not run)
     */
    public Authentication authenticate(String email, Supplier<Authentication> attempt) {
        var key = loginKey(email);
        logins.acquire(key);
        var outcome = Outcome.OTHER;
        try {
            var authentication = attempt.get();
            outcome = Outcome.SUCCESS;
            return authentication;
        } catch (AuthenticationException ex) {
            outcome = Outcome.FAILURE;
            throw ex;
        } finally {
            logins.finish(key, outcome);
        }
    }

    /** The same {@code delegate}, with every sign-in going through {@link #authenticate}. */
    public AuthenticationManager guard(AuthenticationManager delegate) {
        return authentication -> authenticate(authentication.getName(), () -> delegate.authenticate(authentication));
    }

    /**
     * Checks the current password of {@code userId} with {@code matches}; {@code false} counts as a failure.
     *
     * @throws TooManyRequestsException the user's current-password checks are locked (the check is not run)
     */
    public boolean matchesCurrentPassword(int userId, BooleanSupplier matches) {
        currentPasswords.acquire(userId);
        var outcome = Outcome.OTHER;
        try {
            var ok = matches.getAsBoolean();
            outcome = ok ? Outcome.SUCCESS : Outcome.FAILURE;
            return ok;
        } finally {
            currentPasswords.finish(userId, outcome);
        }
    }

    public int trackedKeys() {
        return logins.size() + currentPasswords.size();
    }

    private static String loginKey(String email) {
        var normalized = email == null ? "" : email.toLowerCase(Locale.ROOT);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private enum Outcome { SUCCESS, FAILURE, OTHER }

    private static final class Entry {
        int failures;
        int inFlight;
        int locks;
        Instant lockedUntil;
        Instant lastFailure;

        boolean locked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean stale(Instant now) {
            return lastFailure == null || !now.isBefore(lastFailure.plus(FORGET_AFTER));
        }

        boolean idle() {
            return failures == 0 && inFlight == 0 && locks == 0 && lockedUntil == null;
        }
    }

    private static final class Attempts<K> {

        private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

        private final Clock clock;
        private final Map<K, Entry> entries;
        private Instant lastSweep;

        Attempts(Clock clock, int maxTrackedKeys) {
            this.clock = clock;
            var max = Math.max(1, maxTrackedKeys);
            this.entries = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, Entry> eldest) {
                    return size() > max;
                }
            };
        }

        synchronized void acquire(K key) {
            var now = clock.instant();
            sweepIfDue(now);
            var entry = entries.computeIfAbsent(key, k -> new Entry());
            if (entry.inFlight == 0 && !entry.locked(now) && entry.stale(now)) {
                // A day without failure: the streak and the escalation are forgotten.
                entry.failures = 0;
                entry.locks = 0;
                entry.lockedUntil = null;
            }
            if (entry.locked(now)) {
                var wait = Duration.between(now, entry.lockedUntil);
                throw new TooManyRequestsException(MESSAGE, (wait.toMillis() + 999) / 1000);
            }
            if (entry.failures + entry.inFlight >= MAX_FAILURES) {
                throw new TooManyRequestsException(MESSAGE, 1);
            }
            entry.inFlight++;
        }

        synchronized void finish(K key, Outcome outcome) {
            var now = clock.instant();
            var entry = entries.get(key);
            if (entry == null) {
                // Evicted while the attempt ran: only a failure is worth remembering.
                if (outcome != Outcome.FAILURE) {
                    return;
                }
                entry = new Entry();
                entry.inFlight = 1;
                entries.put(key, entry);
            }
            entry.inFlight = Math.max(0, entry.inFlight - 1);
            switch (outcome) {
                case SUCCESS -> {
                    entry.failures = 0;
                    entry.locks = 0;
                    entry.lockedUntil = null;
                    entry.lastFailure = null;
                }
                case FAILURE -> {
                    entry.lastFailure = now;
                    if (++entry.failures >= MAX_FAILURES) {
                        entry.locks = Math.min(entry.locks + 1, LOCKS.size());
                        entry.lockedUntil = now.plus(LOCKS.get(entry.locks - 1));
                        entry.failures = 0;
                    }
                }
                case OTHER -> { }
            }
            if (entry.idle()) {
                entries.remove(key);
            }
        }

        synchronized int size() {
            return entries.size();
        }

        private void sweepIfDue(Instant now) {
            if (lastSweep != null && Duration.between(lastSweep, now).compareTo(SWEEP_EVERY) < 0) {
                return;
            }
            lastSweep = now;
            for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (entry.inFlight == 0 && !entry.locked(now) && entry.stale(now)) {
                    it.remove();
                }
            }
        }
    }
}
