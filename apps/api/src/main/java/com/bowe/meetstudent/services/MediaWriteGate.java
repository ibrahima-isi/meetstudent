package com.bowe.meetstudent.services;

import com.bowe.meetstudent.exceptions.MediaWritesPausedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Coordinates temporary read-only maintenance pauses for media and media-reference mutations.
 * Tracks active write permits and deferred post-commit file cleanups so cutover tooling can
 * verify both have drained before reconciling storage inventories.
 */
@Component
public class MediaWriteGate {

    private final ReentrantReadWriteLock gateLock = new ReentrantReadWriteLock();
    private final ReentrantLock drainLock = new ReentrantLock();
    private final Condition drainedCondition = drainLock.newCondition();

    private final AtomicBoolean paused;
    private final long retryAfterSeconds;
    private final AtomicInteger inFlightWrites = new AtomicInteger(0);
    private final AtomicInteger pendingDeferredDeletions = new AtomicInteger(0);
    private final AtomicLong rejectedWriteCount = new AtomicLong(0);
    private final AtomicLong completedWriteCount = new AtomicLong(0);
    private final ThreadLocal<Integer> threadDepth = new ThreadLocal<>();

    private volatile Instant pausedAt;
    private volatile Instant resumedAt;

    @Autowired
    public MediaWriteGate(
            @Value("${file.writes-paused:false}") boolean initiallyPaused,
            @Value("${file.writes-retry-after-seconds:60}") long retryAfterSeconds) {
        this.paused = new AtomicBoolean(initiallyPaused);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
        if (initiallyPaused) {
            this.pausedAt = Instant.now();
        }
    }

    /**
     * Acquires an in-flight write permit or throws {@link MediaWritesPausedException} if writes are paused.
     * Reentrant calls on the same thread succeed so already-started operations finish cleanly if a pause begins.
     */
    public Permit enterWrite() {
        Integer currentDepth = threadDepth.get();
        if (currentDepth != null && currentDepth > 0) {
            threadDepth.set(currentDepth + 1);
            return new Permit(false);
        }

        gateLock.readLock().lock();
        try {
            if (paused.get()) {
                rejectedWriteCount.incrementAndGet();
                throw new MediaWritesPausedException(retryAfterSeconds);
            }
            inFlightWrites.incrementAndGet();
            threadDepth.set(1);
            return new Permit(true);
        } finally {
            gateLock.readLock().unlock();
        }
    }

    /** Tracks a deferred post-commit or rollback storage task registered on transaction synchronization. */
    public DeferredTask registerDeferredTask() {
        pendingDeferredDeletions.incrementAndGet();
        return new DeferredTask();
    }

    public Status pause() {
        gateLock.writeLock().lock();
        try {
            if (!paused.getAndSet(true)) {
                pausedAt = Instant.now();
            }
            return status();
        } finally {
            gateLock.writeLock().unlock();
        }
    }

    public Status resume() {
        gateLock.writeLock().lock();
        try {
            if (paused.getAndSet(false)) {
                resumedAt = Instant.now();
            }
            return status();
        } finally {
            gateLock.writeLock().unlock();
        }
    }

    public Status pauseAndAwaitDrain(Duration timeout) {
        pause();
        awaitDrain(timeout);
        return status();
    }

    public boolean awaitDrain(Duration timeout) {
        long remainingNanos = Math.max(0L, timeout.toNanos());
        drainLock.lock();
        try {
            while (!isDrainedCounts()) {
                if (remainingNanos <= 0L) {
                    return false;
                }
                remainingNanos = drainedCondition.awaitNanos(remainingNanos);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            drainLock.unlock();
        }
    }

    public Status status() {
        boolean isPaused = paused.get();
        int active = inFlightWrites.get();
        int deferred = pendingDeferredDeletions.get();
        return new Status(
                isPaused,
                active,
                deferred,
                isPaused && active == 0 && deferred == 0,
                retryAfterSeconds,
                rejectedWriteCount.get(),
                completedWriteCount.get(),
                pausedAt,
                resumedAt
        );
    }

    private boolean isDrainedCounts() {
        return inFlightWrites.get() == 0 && pendingDeferredDeletions.get() == 0;
    }

    private void signalIfDrained() {
        if (!isDrainedCounts()) {
            return;
        }
        drainLock.lock();
        try {
            if (isDrainedCounts()) {
                drainedCondition.signalAll();
            }
        } finally {
            drainLock.unlock();
        }
    }

    public final class Permit implements AutoCloseable {
        private final boolean outer;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Permit(boolean outer) {
            this.outer = outer;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (!outer) {
                Integer depth = threadDepth.get();
                if (depth != null && depth > 1) {
                    threadDepth.set(depth - 1);
                }
                return;
            }
            threadDepth.remove();
            completedWriteCount.incrementAndGet();
            inFlightWrites.decrementAndGet();
            signalIfDrained();
        }
    }

    public final class DeferredTask {
        private final AtomicBoolean completed = new AtomicBoolean(false);

        public void complete() {
            if (completed.compareAndSet(false, true)) {
                pendingDeferredDeletions.decrementAndGet();
                signalIfDrained();
            }
        }
    }

    public record Status(
            boolean writesPaused,
            int inFlightWrites,
            int pendingDeferredDeletions,
            boolean drained,
            long retryAfterSeconds,
            long rejectedWriteCount,
            long completedWriteCount,
            Instant pausedAt,
            Instant resumedAt
    ) {}
}
