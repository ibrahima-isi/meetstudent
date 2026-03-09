package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.ProgramAccreditation;
import com.bowe.meetstudent.entities.embedded.ProgramAccreditationId;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.AccreditationRepository;
import com.bowe.meetstudent.repositories.ProgramAccreditationRepository;
import com.bowe.meetstudent.repositories.ProgramRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProgramAccreditationService {

    private final ProgramRepository programRepository;
    private final AccreditationRepository accreditationRepository;
    private final ProgramAccreditationRepository programAccreditationRepository;

    public List<ProgramAccreditation> findByProgramId(Integer programId) {
        return programAccreditationRepository.findByProgramId(programId);
    }

    public Optional<ProgramAccreditation> findById(Integer programId, Integer accreditationId) {
        return programAccreditationRepository.findById(new ProgramAccreditationId(programId, accreditationId));
    }

    /**
     * Link a program with an accreditation
     * @param programId the id of the program to be linked
     * @param accreditationId the id of the accreditation that will be linked to a program
     * @param startsAt the year of the accreditation
     * @param endsAt the last of the accreditation
     * @return ProgramAccreditation
     */
    @Transactional
    public ProgramAccreditation addAccreditationToProgram(
            Integer programId,
            Integer accreditationId,
            Integer startsAt,
            Integer endsAt
    ){
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Programme non trouvé"));

        Accreditation accreditation = accreditationRepository.findById(accreditationId)
                .orElseThrow(() ->  new ResourceNotFoundException("Accréditation non trouvée"));

        ProgramAccreditation programAccreditation = new ProgramAccreditation();
        programAccreditation.setId(new ProgramAccreditationId(programId, accreditationId));

        programAccreditation.setAccreditation(accreditation);
        programAccreditation.setProgram(program);
        programAccreditation.setStartsAt(startsAt);
        programAccreditation.setEndsAt(endsAt);

        return programAccreditationRepository.save(programAccreditation);
    }

    /**
     * Update program accreditation dates
     * @param programId the id of the program
     * @param accreditationId the id of the accreditation
     * @param startsAt the new start year
     * @param endsAt the new end year
     * @return Updated ProgramAccreditation
     */
    @Transactional
    public ProgramAccreditation updateAccreditationForProgram(
            Integer programId,
            Integer accreditationId,
            Integer startsAt,
            Integer endsAt
    ) {
        ProgramAccreditation pa = programAccreditationRepository.findById(new ProgramAccreditationId(programId, accreditationId))
                .orElseThrow(() -> new ResourceNotFoundException("Association non trouvée"));
        
        pa.setStartsAt(startsAt);
        pa.setEndsAt(endsAt);
        
        return programAccreditationRepository.save(pa);
    }

    /**
     * Delete program accreditation
     * @param programId the id of the program to remove the accreditation from
     * @param accreditationId the id of the accreditation that will be removed from the program
     */
    public void removeAccreditationFromProgram(Integer programId, Integer accreditationId) {
        ProgramAccreditationId id = new ProgramAccreditationId(programId, accreditationId);
        programAccreditationRepository.deleteById(id);
    }
}
