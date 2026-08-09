package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.rates.ProgramRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import org.springframework.data.domain.Sort;

@Repository
public interface ProgramRateRepository extends JpaRepository<ProgramRate, Integer> {
    List<ProgramRate> findByProgramId(Integer programId, Sort sort);

    @Query("SELECT AVG(r.note) FROM ProgramRate r WHERE r.program.id = :programId")
    Double getAverageNoteByProgramId(@Param("programId") Integer programId);
}
