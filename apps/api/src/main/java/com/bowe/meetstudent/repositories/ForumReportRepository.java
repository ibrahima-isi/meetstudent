package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.ForumReport;
import com.bowe.meetstudent.entities.enums.ForumReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface ForumReportRepository extends JpaRepository<ForumReport, Integer> {

    boolean existsByReporter_IdAndThread_Id(Integer reporterId, Integer threadId);

    boolean existsByReporter_IdAndReply_Id(Integer reporterId, Integer replyId);

    @EntityGraph(attributePaths = {"reporter", "thread", "reply", "reply.thread"})
    Page<ForumReport> findBy(Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "thread", "reply", "reply.thread"})
    Page<ForumReport> findByStatus(ForumReportStatus status, Pageable pageable);

    /** The report under a row lock: closing is idempotent and the first decision (and its date) stands. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ForumReport r where r.id = :id")
    Optional<ForumReport> findByIdForUpdate(@Param("id") Integer id);
}
