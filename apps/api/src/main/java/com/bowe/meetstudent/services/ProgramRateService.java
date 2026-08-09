package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.repositories.ProgramRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProgramRateService {
    private final ProgramRateRepository programRateRepository;

    @Transactional
    public ProgramRate save(ProgramRate programRate) {
        return programRateRepository.save(programRate);
    }

    public List<ProgramRate> findByProgramId(Integer programId, org.springframework.data.domain.Sort sort) {
        return programRateRepository.findByProgramId(programId, sort);
    }

    public Double getAverageNoteByProgramId(Integer programId) {
        return programRateRepository.getAverageNoteByProgramId(programId);
    }

    public Optional<ProgramRate> findById(Integer id) {
        return programRateRepository.findById(id);
    }

    public Page<ProgramRate> findAll(Pageable pageable) {
        return programRateRepository.findAll(pageable);
    }

    @Transactional
    public void delete(Integer id) {
        programRateRepository.deleteById(id);
    }
}
