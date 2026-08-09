package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;

@Repository
public interface CourseRepository extends JpaRepository<Course, Integer> {
    Page<Course> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT c FROM Course c LEFT JOIN c.courseRates r GROUP BY c.id ORDER BY AVG(r.note) DESC NULLS LAST")
    Page<Course> findAllByOrderByAverageRateDesc(Pageable pageable);

    @Query("SELECT c FROM Course c LEFT JOIN c.courseRates r GROUP BY c.id ORDER BY AVG(r.note) ASC NULLS LAST")
    Page<Course> findAllByOrderByAverageRateAsc(Pageable pageable);
}
