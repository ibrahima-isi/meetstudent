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
}
