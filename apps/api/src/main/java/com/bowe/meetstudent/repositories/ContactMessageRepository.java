package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Integer> {

    /** Targeted write: only the delivery columns, so it can never overwrite the visitor's text. */
    @Modifying
    @Query("update ContactMessage m set m.mailStatus = :status, m.mailAttempts = :attempts, m.mailSentAt = :sentAt where m.id = :id")
    int updateMailDelivery(@Param("id") Integer id, @Param("status") ContactMailStatus status,
                           @Param("attempts") int attempts, @Param("sentAt") Instant sentAt);

    /**
     * Atomic "claim" of a resend: moves the row back to PENDING only when no delivery is in flight, i.e. it is not
     * PENDING, or it has been PENDING since before {@code staleBefore}. Two concurrent calls cannot both win.
     *
     * @return 1 when claimed, 0 when the row is unknown or its delivery is still considered running
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ContactMessage m set m.mailStatus = com.bowe.meetstudent.entities.enums.ContactMailStatus.PENDING,
                m.mailAttempts = 0, m.mailSentAt = null, m.mailPendingSince = :now
            where m.id = :id and (m.mailStatus <> com.bowe.meetstudent.entities.enums.ContactMailStatus.PENDING
                or coalesce(m.mailPendingSince, m.createdAt) < :staleBefore)""")
    int claimResend(@Param("id") Integer id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
