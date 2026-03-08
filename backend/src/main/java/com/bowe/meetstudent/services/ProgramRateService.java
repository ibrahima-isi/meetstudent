package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.repositories.ProgramRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramRateService {
    private final ProgramRateRepository programRateRepository;

    public ProgramRate save(ProgramRate programRate) {
        return programRateRepository.save(programRate);
    }

    public List<ProgramRate> findByProgramId(Integer programId) {
        return programRateRepository.findByProgramId(programId);
    }

    public Double getAverageNoteByProgramId(Integer programId) {
        return programRateRepository.getAverageNoteByProgramId(programId);
    }
}
