package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Integer> {

    @Query("SELECT DISTINCT p FROM Program p JOIN p.programAccreditations pa JOIN pa.accreditation a WHERE LOWER(a.code) LIKE LOWER(CONCAT('%', :accreditationCode, '%'))")
    List<Program> findProgramByProgramAccreditationsContainingIgnoreCase (@Param("accreditationCode") String accreditationCode);
}
