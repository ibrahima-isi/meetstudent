package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.ProgramAccreditationDTO;
import com.bowe.meetstudent.entities.ProgramAccreditation;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramAccreditationMapper {

    private final ModelMapper modelMapper;

    public ProgramAccreditationDTO toDTO(ProgramAccreditation programAccreditation) {
        return modelMapper.map(programAccreditation, ProgramAccreditationDTO.class);
    }
}
