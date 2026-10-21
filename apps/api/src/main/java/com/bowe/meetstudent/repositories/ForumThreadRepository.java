package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.ForumThread;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ForumThreadRepository extends JpaRepository<ForumThread, Integer> {

    @EntityGraph(attributePaths = {"author", "author.role", "school"})
    Page<ForumThread> findBy(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.role", "school"})
    Page<ForumThread> findByHiddenFalse(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.role", "school"})
    Page<ForumThread> findBySchool_Id(Integer schoolId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.role", "school"})
    Page<ForumThread> findBySchool_IdAndHiddenFalse(Integer schoolId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.role", "school"})
    Optional<ForumThread> findWithAuthorById(Integer id);

    /**
     * The thread under a row lock (SELECT ... FOR UPDATE), for the transactions that read or change {@code locked}:
     * locking/unlocking and {@code createReply} serialise on it, so a reply can never be accepted after a lock committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ForumThread t where t.id = :id")
    Optional<ForumThread> findByIdForUpdate(@Param("id") Integer id);
}
