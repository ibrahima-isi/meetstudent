package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.ProgramDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramMapper implements Mapper<Program, ProgramDTO> {

    private final ModelMapper modelMapper;

    @Override
    public ProgramDTO toDTO(Program program) {
        return modelMapper.map(program, ProgramDTO.class);
    }

    @Override
    public Program toEntity(ProgramDTO programDTO) {
        Program program = modelMapper.map(programDTO, Program.class);
        if (programDTO.getSchoolId() == null) {
            program.setSchool(null);
        }
        return program;
    }
}
