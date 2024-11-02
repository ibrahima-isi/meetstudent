package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.repositories.ProgramRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;

    public ProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    public Program create(Program program) {

        return this.programRepository.save(program);
    }
}
