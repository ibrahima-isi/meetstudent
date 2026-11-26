package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.exceptions.MediaWritesPausedException;
import com.bowe.meetstudent.services.MediaWriteGate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MediaWriteGateTest {

    @Test
    void rejectsWritesWithRetryAfterWhenInitiallyPaused() {
        var gate = new MediaWriteGate(true, 45);

        MediaWritesPausedException ex = assertThrows(MediaWritesPausedException.class, gate::enterWrite);
        assertEquals(45, ex.getRetryAfterSeconds());

        var status = gate.status();
        assertTrue(status.writesPaused());
        assertTrue(status.drained());
        assertEquals(1, status.rejectedWriteCount());
        assertEquals(0, status.inFlightWrites());
    }

    @Test
    void tracksInFlightWritesAndSupportsReentrantCallsDuringPause() {
        var gate = new MediaWriteGate(false, 60);

        try (var outer = gate.enterWrite()) {
            assertEquals(1, gate.status().inFlightWrites());

            gate.pause();
            assertFalse(gate.status().drained());

            // Nested write on the same thread finishes the already-started operation.
            try (var inner = gate.enterWrite()) {
                assertEquals(1, gate.status().inFlightWrites());
            }
            assertEquals(1, gate.status().inFlightWrites());
        }

        var status = gate.status();
        assertTrue(status.writesPaused());
        assertTrue(status.drained());
        assertEquals(0, status.inFlightWrites());
        assertEquals(1, status.completedWriteCount());

        assertThrows(MediaWritesPausedException.class, gate::enterWrite);
        gate.resume();
        assertFalse(gate.status().writesPaused());
        try (var afterResume = gate.enterWrite()) {
            assertEquals(1, gate.status().inFlightWrites());
        }
        assertEquals(2, gate.status().completedWriteCount());
    }

    @Test
    void awaitDrainWaitsForBothInFlightWritesAndDeferredTasks() throws Exception {
        var gate = new MediaWriteGate(false, 60);
        var permit = gate.enterWrite();
        var deferred = gate.registerDeferredTask();

        assertFalse(gate.pauseAndAwaitDrain(Duration.ofMillis(50)).drained());
        assertEquals(1, gate.status().inFlightWrites());
        assertEquals(1, gate.status().pendingDeferredDeletions());

        var pool = Executors.newSingleThreadExecutor();
        var drainedLatch = new CountDownLatch(1);
        try {
            var future = pool.submit(() -> {
                boolean ok = gate.awaitDrain(Duration.ofSeconds(2));
                if (ok) {
                    drainedLatch.countDown();
                }
                return ok;
            });

            permit.close();
            assertFalse(drainedLatch.await(50, TimeUnit.MILLISECONDS));
            assertEquals(1, gate.status().pendingDeferredDeletions());

            deferred.complete();
            assertTrue(future.get(2, TimeUnit.SECONDS));
            assertTrue(gate.status().drained());
        } finally {
            pool.shutdownNow();
        }
    }
}
