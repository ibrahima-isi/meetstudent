package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.ForumReply;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ForumReplyRepository extends JpaRepository<ForumReply, Integer> {

    @EntityGraph(attributePaths = {"author", "author.role"})
    Page<ForumReply> findByThread_Id(Integer threadId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.role"})
    Page<ForumReply> findByThread_IdAndHiddenFalse(Integer threadId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.role", "thread"})
    Optional<ForumReply> findWithAuthorById(Integer id);

    /** The reply under a row lock (SELECT ... FOR UPDATE): every writer of {@code hidden} or of the text goes through it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ForumReply r where r.id = :id")
    Optional<ForumReply> findByIdForUpdate(@Param("id") Integer id);

    /** Rows of (thread id, number of replies) for the given threads; hidden replies only count when asked. */
    @Query("select r.thread.id, count(r) from ForumReply r where r.thread.id in :ids "
            + "and (:includeHidden = true or r.hidden = false) group by r.thread.id")
    List<Object[]> countByThreadIds(@Param("ids") Collection<Integer> ids, @Param("includeHidden") boolean includeHidden);
}
