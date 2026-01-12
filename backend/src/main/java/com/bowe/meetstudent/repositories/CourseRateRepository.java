package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.rates.CourseRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRateRepository extends JpaRepository<CourseRate, Integer> {
}
