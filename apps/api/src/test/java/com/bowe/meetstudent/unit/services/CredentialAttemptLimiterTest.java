package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.exceptions.TooManyRequestsException;
import com.bowe.meetstudent.services.CredentialAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialAttemptLimiterTest {

    private static final Authentication OK = new UsernamePasswordAuthenticationToken("someone", null, java.util.List.of());

    private final ContactRateLimiterTest.MutableClock clock = new ContactRateLimiterTest.MutableClock(Instant.parse("2026-10-28T10:00:00Z"));
    private final CredentialAttemptLimiter limiter = new CredentialAttemptLimiter(clock);
    private final AtomicInteger checks = new AtomicInteger();

    private Authentication right(String email) {
        return limiter.authenticate(email, () -> {
            checks.incrementAndGet();
            return OK;
        });
    }

    private void wrong(String email) {
        assertThatThrownBy(() -> limiter.authenticate(email, () -> {
            checks.incrementAndGet();
            throw new BadCredentialsException("Bad credentials");
        })).isInstanceOf(BadCredentialsException.class);
    }

    private void wrong(String email, int times) {
        for (int i = 0; i < times; i++) {
            wrong(email);
        }
    }

    private void lockedFor(String email, long seconds) {
        var before = checks.get();
        assertThatThrownBy(() -> right(email))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(seconds));
        assertThat(checks.get()).as("the password is not even checked while locked").isEqualTo(before);
    }

    // ---- login ---------------------------------------------------------------------------------

    @Test
    void fiveFailuresLockTheAccountForOneMinuteEvenWithTheRightPassword() {
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 60);
        clock.advance(Duration.ofSeconds(20));
        lockedFor("a@x.com", 40);
    }

    @Test
    void fourFailuresDoNotLock() {
        wrong("a@x.com", 4);
        assertThat(right("a@x.com")).isSameAs(OK);
    }

    @Test
    void afterTheLockTheRightPasswordWorksAndResetsTheStreak() {
        wrong("a@x.com", 5);
        clock.advance(Duration.ofSeconds(60));
        assertThat(right("a@x.com")).isSameAs(OK);
        // Reset: a new streak starts from zero and the next lock is the first level again.
        wrong("a@x.com", 4);
        assertThat(right("a@x.com")).isSameAs(OK);
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 60);
    }

    @Test
    void aSuccessBeforeFiveFailuresResetsTheCounter() {
        wrong("a@x.com", 4);
        right("a@x.com");
        wrong("a@x.com", 4);
        assertThat(right("a@x.com")).isSameAs(OK);
    }

    @Test
    void locksEscalateFromOneToFiveToFifteenMinutesAndStayCapped() {
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 60);
        clock.advance(Duration.ofMinutes(1));
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 300);
        clock.advance(Duration.ofMinutes(5));
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 900);
        clock.advance(Duration.ofMinutes(15));
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 900);
    }

    @Test
    void theEscalationIsForgottenADayAfterTheLastFailure() {
        wrong("a@x.com", 5);
        clock.advance(Duration.ofHours(24));
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 60);
    }

    @Test
    void otherAccountsAreUnaffected() {
        wrong("a@x.com", 5);
        lockedFor("a@x.com", 60);
        assertThat(right("b@x.com")).isSameAs(OK);
    }

    @Test
    void theKeyIsTheLowerCasedEmail() {
        wrong("Mixed@X.com", 3);
        wrong("mixed@x.COM", 2);
        lockedFor("MIXED@x.com", 60);
    }

    @Test
    void aNullUsernameIsLimitedLikeAnyOtherKey() {
        wrong(null, 5);
        lockedFor(null, 60);
        assertThat(right("a@x.com")).isSameAs(OK);
    }

    @Test
    void errorsThatAreNotAWrongPasswordDoNotCount() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> limiter.authenticate("a@x.com", () -> {
                throw new IllegalStateException("database down");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(right("a@x.com")).isSameAs(OK);
    }

    @Test
    void attemptsInFlightCountSoParallelGuessesCannotExceedTheBudget() {
        // Five guesses still running (nested here, as parallel requests would be) leave no room for a sixth.
        var sixth = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        nested("a@x.com", 5, sixth);
        assertThat(sixth.get()).isInstanceOfSatisfying(TooManyRequestsException.class,
                ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(1));
    }

    private void nested(String email, int depth, java.util.concurrent.atomic.AtomicReference<Throwable> sixth) {
        if (depth == 0) {
            try {
                right(email);
            } catch (TooManyRequestsException ex) {
                sixth.set(ex);
            }
            return;
        }
        assertThatThrownBy(() -> limiter.authenticate(email, () -> {
            nested(email, depth - 1, sixth);
            throw new BadCredentialsException("Bad credentials");
        })).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void trackedKeysAreBounded() {
        var small = new CredentialAttemptLimiter(clock, 50);
        for (int i = 0; i < 500; i++) {
            var email = "user" + i + "@x.com";
            assertThatThrownBy(() -> small.authenticate(email, () -> {
                throw new BadCredentialsException("Bad credentials");
            })).isInstanceOf(BadCredentialsException.class);
        }
        assertThat(small.trackedKeys()).isLessThanOrEqualTo(50);
    }

    @Test
    void finishedKeysAreForgotten() {
        wrong("a@x.com", 2);
        right("a@x.com");
        assertThat(limiter.trackedKeys()).isZero();
        wrong("b@x.com", 5);
        clock.advance(Duration.ofHours(25));
        right("c@x.com");
        assertThat(limiter.trackedKeys()).isZero();
    }

    // ---- current password ----------------------------------------------------------------------

    @Test
    void currentPasswordChecksLockPerUserAfterFiveFailures() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.matchesCurrentPassword(7, () -> false)).isFalse();
        }
        var asked = new AtomicInteger();
        assertThatThrownBy(() -> limiter.matchesCurrentPassword(7, () -> {
            asked.incrementAndGet();
            return true;
        })).isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(60));
        assertThat(asked).hasValue(0);
        assertThat(limiter.matchesCurrentPassword(8, () -> true)).isTrue();
        clock.advance(Duration.ofSeconds(60));
        assertThat(limiter.matchesCurrentPassword(7, () -> true)).isTrue();
    }

    @Test
    void currentPasswordSuccessResetsAndEscalationMatchesTheLogin() {
        for (int i = 0; i < 4; i++) limiter.matchesCurrentPassword(7, () -> false);
        assertThat(limiter.matchesCurrentPassword(7, () -> true)).isTrue();
        for (int i = 0; i < 4; i++) limiter.matchesCurrentPassword(7, () -> false);
        assertThat(limiter.matchesCurrentPassword(7, () -> true)).isTrue();
        for (int i = 0; i < 5; i++) limiter.matchesCurrentPassword(7, () -> false);
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 5; i++) limiter.matchesCurrentPassword(7, () -> false);
        assertThatThrownBy(() -> limiter.matchesCurrentPassword(7, () -> true))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(300));
    }

    @Test
    void loginAndCurrentPasswordAreSeparateNamespaces() {
        for (int i = 0; i < 5; i++) limiter.matchesCurrentPassword(7, () -> false);
        wrong("7", 4);
        assertThatCode(() -> right("7")).doesNotThrowAnyException();
        wrong("a@x.com", 5);
        assertThat(limiter.matchesCurrentPassword(8, () -> true)).isTrue();
    }

    // ---- keys that match what the database compares -------------------------------------------

    @Test
    void unknownEmailsAreNormalisedLikeTheDatabaseUpperComparison() {
        // Postgres upper(): 'ſ' (long s) -> 'S', 'ı' (dotless i) -> 'I'. The variants must not multiply the budget.
        wrong("ſtudent@x.com", 2);
        wrong("STUDENT@x.com", 2);
        wrong("student@x.com", 1);
        lockedFor("Student@X.com", 60);
        wrong("ıd@x.com", 3);
        wrong("id@x.com", 2);
        lockedFor("ID@x.com", 60);
    }

    @Test
    void aKnownAccountIsKeyedByItsIdWhateverSpellingFindsIt() {
        var manager = limiter.guard(authentication -> {
            throw new BadCredentialsException("Bad credentials");
        }, email -> java.util.Optional.of(42));
        var spellings = java.util.List.of("ſtudent@x.com", "STUDENT@x.com", "ştudent@x.com", "student@x.com", "x");
        for (var spelling : spellings) {
            assertThatThrownBy(() -> manager.authenticate(new UsernamePasswordAuthenticationToken(spelling, "pw")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> manager.authenticate(new UsernamePasswordAuthenticationToken("anything", "pw")))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(60));
        // Another account, and the email namespace of unknown addresses, are untouched.
        assertThat(limiter.authenticateAccount(43, "other@x.com", () -> OK)).isSameAs(OK);
        assertThat(right("student@x.com")).isSameAs(OK);
    }

    /**
     * NFKC folds more than the database (fullwidth 'ａ' does not find "alice"): failures under such a spelling lock its
     * email key, so a known account must check and count that key too, or the lock would reveal which accounts exist.
     */
    @Test
    void aKnownAccountAlsoUsesTheEmailKeySoTheLockRevealsNothing() {
        java.util.function.Function<String, java.util.Optional<Integer>> db =
                email -> email.equalsIgnoreCase("alice@x.com") ? java.util.Optional.of(42) : java.util.Optional.empty();
        java.util.function.Function<String, Integer> outcome = name -> {
            var manager = limiter.guard(authentication -> OK, db);
            try {
                manager.authenticate(new UsernamePasswordAuthenticationToken(name, "pw"));
                return 200;
            } catch (TooManyRequestsException ex) {
                return 429;
            }
        };
        var failing = limiter.guard(authentication -> {
            throw new BadCredentialsException("Bad credentials");
        }, db);
        for (var target : java.util.List.of("ａlice@x.com", "ａnyone@x.com")) {
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> failing.authenticate(new UsernamePasswordAuthenticationToken(target, "pw")))
                        .isInstanceOf(BadCredentialsException.class);
            }
        }
        assertThat(outcome.apply("alice@x.com")).as("known").isEqualTo(429);
        assertThat(outcome.apply("anyone@x.com")).as("unknown").isEqualTo(429);
    }

    @Test
    void aKnownAccountCountsEachFailureOnBothKeys() {
        var failing = limiter.guard(authentication -> {
            throw new BadCredentialsException("Bad credentials");
        }, email -> java.util.Optional.of(42));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> failing.authenticate(new UsernamePasswordAuthenticationToken("bob@x.com", "pw")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        // The email key is locked as well: the address keeps its lock even if the lookup stops finding the account.
        lockedFor("bob@x.com", 60);
        assertThatThrownBy(() -> limiter.authenticateAccount(42, "elsewhere@x.com", () -> OK)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void anUnknownEmailGoesThroughTheEmailKey() {
        var manager = limiter.guard(authentication -> {
            throw new BadCredentialsException("Bad credentials");
        }, email -> java.util.Optional.empty());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> manager.authenticate(new UsernamePasswordAuthenticationToken("ſomeone@x.com", "pw")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        lockedFor("someone@x.com", 60);
    }

    // ---- eviction never frees a locked or escalated key ----------------------------------------

    @Test
    void floodingNewKeysDoesNotEvictALockOrItsEscalation() {
        var small = new CredentialAttemptLimiter(clock, 50);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> small.authenticate("victim@x.com", () -> {
                throw new BadCredentialsException("Bad credentials");
            })).isInstanceOf(BadCredentialsException.class);
        }
        for (int i = 0; i < 500; i++) {
            var email = "flood" + i + "@x.com";
            assertThatThrownBy(() -> small.authenticate(email, () -> {
                throw new BadCredentialsException("Bad credentials");
            })).isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> small.authenticate("victim@x.com", () -> OK)).isInstanceOf(TooManyRequestsException.class);
        // Once the lock is over the escalation is still remembered: the next streak locks for 5 minutes.
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 500; i++) {
            var email = "flood-again" + i + "@x.com";
            assertThatThrownBy(() -> small.authenticate(email, () -> {
                throw new BadCredentialsException("Bad credentials");
            })).isInstanceOf(BadCredentialsException.class);
        }
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> small.authenticate("victim@x.com", () -> {
                throw new BadCredentialsException("Bad credentials");
            })).isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> small.authenticate("victim@x.com", () -> OK))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(300));
    }

    @Test
    void escalatedKeysHaveTheirOwnHardCap() {
        var small = new CredentialAttemptLimiter(clock, 50);
        for (int k = 0; k < 1000; k++) {
            var email = "locked" + k + "@x.com";
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> small.authenticate(email, () -> {
                    throw new BadCredentialsException("Bad credentials");
                })).isInstanceOf(BadCredentialsException.class);
            }
        }
        assertThat(small.trackedKeys()).isLessThanOrEqualTo(50 + 50 * CredentialAttemptLimiter.ESCALATED_CAP_FACTOR);
    }
}
