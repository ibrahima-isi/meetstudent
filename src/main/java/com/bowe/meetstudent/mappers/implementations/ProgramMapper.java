package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.ProgramDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramMapper implements Mapper<Program, ProgramDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final MediaMapper mediaMapper;

    @Override
    public ProgramDTO toDTO(Program program) {
        ProgramDTO dto = modelMapper.map(program, ProgramDTO.class);
        dto.setPhotoMediaId(program.getPhotoMediaId());
        dto.setPhoto(mediaService.findById(program.getPhotoMediaId()).map(mediaMapper::toDTO).orElse(null));
        return dto;
    }

    @Override
    public Program toEntity(ProgramDTO programDTO) {
        Program program = modelMapper.map(programDTO, Program.class);
        program.setPhotoMediaId(programDTO.getPhotoMediaId());
        if (programDTO.getSchoolId() == null) {
            program.setSchool(null);
        }
        if (program.getCourses() != null) {
            program.getCourses().forEach(c -> c.setProgram(program));
        }
        return program;
    }
}
