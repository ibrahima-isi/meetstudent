package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Integer> {
    Optional<Tag> findByNameIgnoreCase(String name);
}
