package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course, Integer> {
    Page<Course> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
