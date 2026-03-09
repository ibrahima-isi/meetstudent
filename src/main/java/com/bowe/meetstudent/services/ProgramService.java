package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.repositories.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programRepository;
    private final MediaService mediaService;

    @Transactional
    public Program save(Program program) {
        return this.programRepository.save(program);
    }

    public Page<Program> findAll(Pageable pageable) {
        return this.programRepository.findAll(pageable);
    }

    public Optional<Program> findById(int id) {
        return this.programRepository.findById(id);
    }

    public Page<Program> findByName(String name, Pageable pageable) {
        String safeName = (name == null) ? "" : name;
        return this.programRepository.findByNameContainingIgnoreCase(safeName, pageable);
    }

    public boolean exists(int id) {
        return this.programRepository.existsById(id);
    }

    @Transactional
    public void delete(int id) {
        this.programRepository.findById(id).ifPresent(program -> {
            mediaService.deleteMediaByUrl(program.getPhotoUrl());
            this.programRepository.deleteById(id);
        });
    }

    public Page<Program> findAllOrderByRateDesc(Pageable pageable) {
        return programRepository.findAllByOrderByAverageRateDesc(pageable);
    }

    public Page<Program> findAllOrderByRateAsc(Pageable pageable) {
        return programRepository.findAllByOrderByAverageRateAsc(pageable);
    }

    public List<Program> findProgramByAccreditationCode(String code) {
        String safeCode = (code == null) ? "" : code;
        return programRepository.findProgramByProgramAccreditationsContainingIgnoreCase(safeCode);
    }

    @Transactional
    public Program patch(Integer id, Program updates) {
        return programRepository.findById(id).map(existing -> {
            mediaService.deleteOldMediaIfChanged(existing.getPhotoUrl(), updates.getPhotoUrl());
            
            if (updates.getName() != null) existing.setName(updates.getName());
            if (updates.getCode() != null) existing.setCode(updates.getCode());
            if (updates.getDuration() != null) existing.setDuration(updates.getDuration());
            if (updates.getPhotoUrl() != null) existing.setPhotoUrl(updates.getPhotoUrl());
            if (updates.getSchool() != null) existing.setSchool(updates.getSchool());
            
            return programRepository.save(existing);
        }).orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("Program not found"));
    }
}
