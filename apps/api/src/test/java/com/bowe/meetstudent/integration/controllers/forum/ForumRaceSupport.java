package com.bowe.meetstudent.integration.controllers.forum;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic two-transaction races. {@link Held} runs an action in a transaction and keeps it open (the row locks
 * it took stay held) until {@link Held#release()}. {@link #startAndAwaitBlocked} starts a second action on its own
 * thread and returns only once H2 reports that session blocked by another one and the action has not finished: no
 * sleep decides the outcome.
 */
final class ForumRaceSupport {

    private ForumRaceSupport() {
    }

    static final class Held {
        private final CountDownLatch held = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final FutureTask<Void> task;

        Held(PlatformTransactionManager transactions, Runnable action) {
            this.task = new FutureTask<>(() -> {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    action.run();
                    held.countDown();
                    try {
                        release.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                return null;
            });
            new Thread(task, "race-held").start();
        }

        /** Waits until the action ran (its locks are taken, nothing committed yet). */
        Held awaitHeld() throws InterruptedException {
            assertThat(held.await(10, TimeUnit.SECONDS)).as("the first transaction took its locks").isTrue();
            return this;
        }

        /** Commits the held transaction and waits for it. */
        void release() throws Exception {
            release.countDown();
            task.get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Starts the action on its own thread and returns once H2 itself reports a session blocked by another one
     * (INFORMATION_SCHEMA.SESSIONS.BLOCKER_ID, i.e. a real row-lock wait), with the action still unfinished.
     */
    static <T> Future<T> startAndAwaitBlocked(DataSource dataSource, Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        new Thread(task, "race-blocked").start();
        try (var connection = dataSource.getConnection(); var st = connection.createStatement()) {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!task.isDone() && blockedSessions(st) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(2);
            }
            assertThat(task.isDone()).as("the second transaction must wait for the row lock, not be decided on a stale read").isFalse();
            assertThat(blockedSessions(st)).as("H2 reports the second session blocked by the first").isGreaterThanOrEqualTo(1);
        }
        return task;
    }

    private static int blockedSessions(Statement st) throws SQLException {
        try (var rs = st.executeQuery("select count(*) from information_schema.sessions where blocker_id is not null")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static <T> T get(Future<T> future) throws Exception {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }
}
