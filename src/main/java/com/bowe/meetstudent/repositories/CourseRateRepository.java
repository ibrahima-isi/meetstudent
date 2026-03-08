package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.rates.CourseRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface CourseRateRepository extends JpaRepository<CourseRate, Integer> {
    List<CourseRate> findByCourseId(Integer courseId);

    @Query("SELECT AVG(r.note) FROM CourseRate r WHERE r.course.id = :courseId")
    Double getAverageNoteByCourseId(@Param("courseId") Integer courseId);
}
