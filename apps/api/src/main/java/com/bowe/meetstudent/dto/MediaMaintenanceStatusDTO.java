package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.services.MediaWriteGate;

import java.time.Instant;

public record MediaMaintenanceStatusDTO(
        boolean writesPaused,
        String storageBackend,
        int inFlightWrites,
        int pendingDeferredDeletions,
        boolean drained,
        long retryAfterSeconds,
        long rejectedWriteCount,
        long completedWriteCount,
        Instant pausedAt,
        Instant resumedAt
) {
    public static MediaMaintenanceStatusDTO from(MediaWriteGate.Status status, String storageBackend) {
        return new MediaMaintenanceStatusDTO(
                status.writesPaused(),
                storageBackend,
                status.inFlightWrites(),
                status.pendingDeferredDeletions(),
                status.drained(),
                status.retryAfterSeconds(),
                status.rejectedWriteCount(),
                status.completedWriteCount(),
                status.pausedAt(),
                status.resumedAt()
        );
    }
}
