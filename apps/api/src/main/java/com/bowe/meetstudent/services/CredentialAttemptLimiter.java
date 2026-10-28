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
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Progressive lockout against password guessing, kept in memory (one API instance, reset on restart, like
 * {@link ForumRateLimiter}). Two separate namespaces: <b>sign-in</b> and <b>current password</b> checks (email
 * change, account deletion, password change, keyed by user id).
 * <p>
 * A sign-in is keyed by the <b>id of the account</b> the address finds ({@link #guard}), so every spelling the
 * database accepts for it (case, and the Unicode letters its {@code upper()} folds, such as 'ſ' -> 'S' or
 * 'ı' -> 'I') shares one budget. An address that finds no account is keyed by its NFKC upper-cased form (at least
 * as coarse as the database comparison), stored as a SHA-256, so the size of a key is fixed and no address is kept.
 * Both paths do the same work, so an unknown address is limited, answered and timed like a known one.
 * <p>
 * After {@value #MAX_FAILURES} consecutive failures the key is locked for 1 minute, after the next streak 5 minutes,
 * then 15 minutes (cap); a success resets everything, and a key with no failure for a day is forgotten. While locked
 * the password is not checked at all and {@link TooManyRequestsException} (429 + {@code Retry-After}) is thrown.
 * Attempts still running count against the streak, so parallel guesses cannot exceed it. Errors that are not a wrong
 * password (database down...) do not count.
 * <p>
 * Memory is bounded per namespace: at most {@code maxTrackedKeys} keys that were never locked (least recently seen
 * dropped first), and locked or escalated keys apart, which flooding new keys never evicts, up to a hard cap of
 * {@value #ESCALATED_CAP_FACTOR} times that (oldest lock dropped first); finished entries are swept about once a minute.
 */
@Component
public class CredentialAttemptLimiter {

    public static final int MAX_FAILURES = 5;
    public static final int ESCALATED_CAP_FACTOR = 4;
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
     * Runs one sign-in attempt keyed by {@code email} (use it for an address that finds no account): an
     * {@link AuthenticationException} counts as a failure, a returned authentication as a success.
     *
     * @throws TooManyRequestsException the key is locked (the attempt is not run)
     */
    public Authentication authenticate(String email, Supplier<Authentication> attempt) {
        return signIn(emailKey(email), attempt);
    }

    /** {@link #authenticate} for an address that finds the account {@code userId}: keyed by the id. */
    public Authentication authenticateAccount(int userId, Supplier<Authentication> attempt) {
        return signIn("id:" + userId, attempt);
    }

    /**
     * The same {@code delegate}, with every sign-in going through the lockout, keyed by the account
     * {@code accountIdByEmail} finds for the submitted name (the same case-insensitive lookup as the sign-in itself),
     * or by the normalised address when it finds none.
     */
    public AuthenticationManager guard(AuthenticationManager delegate, Function<String, Optional<Integer>> accountIdByEmail) {
        return authentication -> {
            var name = authentication.getName();
            Supplier<Authentication> attempt = () -> delegate.authenticate(authentication);
            var accountId = name == null || name.isEmpty() ? Optional.<Integer>empty() : accountIdByEmail.apply(name);
            return accountId.isPresent() ? authenticateAccount(accountId.get(), attempt) : authenticate(name, attempt);
        };
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

    private Authentication signIn(String key, Supplier<Authentication> attempt) {
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

    private static String emailKey(String email) {
        var normalized = email == null ? "" : Normalizer.normalize(email, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "em:" + HexFormat.of().formatHex(digest);
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

        boolean escalated() {
            return locks > 0;
        }

        boolean idle() {
            return failures == 0 && inFlight == 0 && locks == 0 && lockedUntil == null;
        }
    }

    /**
     * One namespace. Entries that were never locked live in {@code fresh} (access order, LRU-capped); once locked
     * they move to {@code escalated} (insertion order, larger hard cap), so a flood of new keys cannot push them out.
     */
    private static final class Attempts<K> {

        private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

        private final Clock clock;
        private final Map<K, Entry> fresh;
        private final Map<K, Entry> escalated;
        private Instant lastSweep;

        Attempts(Clock clock, int maxTrackedKeys) {
            this.clock = clock;
            var max = Math.max(1, maxTrackedKeys);
            var maxEscalated = Math.max(1, max * ESCALATED_CAP_FACTOR);
            this.fresh = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, Entry> eldest) {
                    return size() > max;
                }
            };
            this.escalated = new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, Entry> eldest) {
                    return size() > maxEscalated;
                }
            };
        }

        private Entry get(K key) {
            var entry = escalated.get(key);
            return entry != null ? entry : fresh.get(key);
        }

        private void remove(K key) {
            escalated.remove(key);
            fresh.remove(key);
        }

        synchronized void acquire(K key) {
            var now = clock.instant();
            sweepIfDue(now);
            var entry = get(key);
            if (entry == null) {
                entry = new Entry();
                fresh.put(key, entry);
            }
            if (entry.inFlight == 0 && !entry.locked(now) && entry.stale(now)) {
                // A day without failure: the streak and the escalation are forgotten.
                entry.failures = 0;
                entry.locks = 0;
                entry.lockedUntil = null;
                if (escalated.remove(key) != null) {
                    fresh.put(key, entry);
                }
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
            var entry = get(key);
            if (entry == null) {
                // Evicted while the attempt ran: only a failure is worth remembering.
                if (outcome != Outcome.FAILURE) {
                    return;
                }
                entry = new Entry();
                entry.inFlight = 1;
                fresh.put(key, entry);
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
                        // (Re)inserted last: the hard cap drops the oldest lock first, never a fresh one.
                        fresh.remove(key);
                        escalated.remove(key);
                        escalated.put(key, entry);
                    }
                }
                case OTHER -> { }
            }
            if (entry.idle()) {
                remove(key);
            } else if (!entry.escalated() && escalated.remove(key) != null) {
                fresh.put(key, entry);
            }
        }

        synchronized int size() {
            return fresh.size() + escalated.size();
        }

        private void sweepIfDue(Instant now) {
            if (lastSweep != null && Duration.between(lastSweep, now).compareTo(SWEEP_EVERY) < 0) {
                return;
            }
            lastSweep = now;
            sweep(fresh, now);
            sweep(escalated, now);
        }

        private static void sweep(Map<?, Entry> entries, Instant now) {
            for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (entry.inFlight == 0 && !entry.locked(now) && entry.stale(now)) {
                    it.remove();
                }
            }
        }
    }
}
