package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Integer> {

    @Query("SELECT DISTINCT p FROM Program p JOIN p.programAccreditations pa JOIN pa.accreditation a WHERE LOWER(a.code) LIKE LOWER(CONCAT('%', :accreditationCode, '%'))")
    List<Program> findProgramByProgramAccreditationsContainingIgnoreCase (@Param("accreditationCode") String accreditationCode);

    Page<Program> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT p FROM Program p LEFT JOIN p.programRates r GROUP BY p.id ORDER BY AVG(r.note) DESC NULLS LAST")
    Page<Program> findAllByOrderByAverageRateDesc(Pageable pageable);

    @Query("SELECT p FROM Program p LEFT JOIN p.programRates r GROUP BY p.id ORDER BY AVG(r.note) ASC NULLS LAST")
    Page<Program> findAllByOrderByAverageRateAsc(Pageable pageable);
}
