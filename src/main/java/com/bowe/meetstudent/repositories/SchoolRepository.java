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
           "WHERE (:city IS NULL OR LOWER(s.address.city) = LOWER(:city)) " +
           "AND (:country IS NULL OR LOWER(s.address.country) = LOWER(:country)) " +
           "AND (:programName IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :programName, '%')))")
    Page<School> searchSchools(
            @Param("city") String city, 
            @Param("country") String country, 
            @Param("programName") String programName, 
            Pageable pageable);

    @Query("SELECT s FROM School s LEFT JOIN s.schoolRates r GROUP BY s.id ORDER BY AVG(r.note) DESC NULLS LAST")
    Page<School> findAllByOrderByAverageRateDesc(Pageable pageable);

    @Query("SELECT s FROM School s LEFT JOIN s.schoolRates r GROUP BY s.id ORDER BY AVG(r.note) ASC NULLS LAST")
    Page<School> findAllByOrderByAverageRateAsc(Pageable pageable);
}
