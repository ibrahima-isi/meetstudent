package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.rates.SchoolRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import org.springframework.data.domain.Sort;

@Repository
public interface SchoolRateRepository extends JpaRepository<SchoolRate, Integer> {
    List<SchoolRate> findBySchoolId(Integer schoolId, Sort sort);

    @Query("SELECT AVG(r.note) FROM SchoolRate r WHERE r.school.id = :schoolId")
    Double getAverageNoteBySchoolId(@Param("schoolId") Integer schoolId);
}
