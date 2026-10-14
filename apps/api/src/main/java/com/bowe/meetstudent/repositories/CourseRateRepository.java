package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.rates.CourseRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import org.springframework.data.domain.Sort;

@Repository
public interface CourseRateRepository extends JpaRepository<CourseRate, Integer> {
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("delete from CourseRate r where r.userEntity.id = :userId")
    int deleteAllByUserId(@Param("userId") Integer userId);

    List<CourseRate> findByCourseId(Integer courseId, Sort sort);

    @Query("SELECT AVG(r.note) FROM CourseRate r WHERE r.course.id = :courseId")
    Double getAverageNoteByCourseId(@Param("courseId") Integer courseId);
}
