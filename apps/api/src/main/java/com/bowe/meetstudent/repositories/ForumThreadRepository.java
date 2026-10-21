package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.ForumThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
