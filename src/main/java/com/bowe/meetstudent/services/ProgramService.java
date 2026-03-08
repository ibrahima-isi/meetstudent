package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.repositories.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programRepository;

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
        return this.programRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public boolean exists(int id) {
        return this.programRepository.existsById(id);
    }

    @Transactional
    public void delete(int id) {
        this.programRepository.deleteById(id);
    }
}
