package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

@Repository
public interface SchoolRepository extends JpaRepository<School, Integer> {

    Page<School> findSchoolByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<School> findSchoolByAddress_City(String city, Pageable pageable);

    @Query("SELECT s FROM School s LEFT JOIN s.schoolRates r GROUP BY s.id ORDER BY AVG(r.note) DESC NULLS LAST")
    Page<School> findAllByOrderByAverageRateDesc(Pageable pageable);

    @Query("SELECT s FROM School s LEFT JOIN s.schoolRates r GROUP BY s.id ORDER BY AVG(r.note) ASC NULLS LAST")
    Page<School> findAllByOrderByAverageRateAsc(Pageable pageable);
}
