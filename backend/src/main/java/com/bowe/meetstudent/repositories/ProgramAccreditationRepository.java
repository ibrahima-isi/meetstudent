package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.ProgramAccreditation;
import com.bowe.meetstudent.entities.embedded.ProgramAccreditationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramAccreditationRepository extends JpaRepository<ProgramAccreditation, ProgramAccreditationId> {

    List<ProgramAccreditation> findByProgramId(Integer programId);
    List<ProgramAccreditation> findByAccreditationCode(String code);
}
