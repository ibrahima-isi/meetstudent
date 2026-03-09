package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

@Repository
public interface SchoolRepository extends JpaRepository<School, Integer> {

    Page<School> findSchoolByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<School> findSchoolByAddress_City(String city, Pageable pageable);

    Page<School> findSchoolByAddress_Country(String country, Pageable pageable);

    @Query("SELECT DISTINCT s FROM School s " +
           "LEFT JOIN s.programs p " +
           "LEFT JOIN s.tags t " +
           "WHERE (:city = '' OR LOWER(s.address.city) = LOWER(CAST(:city AS text))) " +
           "AND (:country = '' OR LOWER(s.address.country) = LOWER(CAST(:country AS text))) " +
           "AND (:programName = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:programName AS text), '%'))) " +
           "AND (:tagName = '' OR LOWER(t.name) = LOWER(CAST(:tagName AS text)))")
    Page<School> searchSchools(
            @Param("city") String city, 
            @Param("country") String country, 
            @Param("programName") String programName, 
            @Param("tagName") String tagName,
            Pageable pageable);

    @Query("SELECT s FROM School s LEFT JOIN s.schoolRates r GROUP BY s.id ORDER BY AVG(r.note) DESC NULLS LAST")
    Page<School> findAllByOrderByAverageRateDesc(Pageable pageable);

    @Query("SELECT s FROM School s LEFT JOIN s.schoolRates r GROUP BY s.id ORDER BY AVG(r.note) ASC NULLS LAST")
    Page<School> findAllByOrderByAverageRateAsc(Pageable pageable);
}
