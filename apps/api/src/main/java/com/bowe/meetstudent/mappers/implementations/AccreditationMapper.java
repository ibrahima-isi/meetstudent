package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.AccreditationDTO;
import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccreditationMapper implements Mapper<Accreditation, AccreditationDTO> {

    private final ModelMapper modelMapper;

    @Override
    public AccreditationDTO toDTO(Accreditation accreditation) {
        return modelMapper.map(accreditation, AccreditationDTO.class);
    }

    @Override
    public Accreditation toEntity(AccreditationDTO accreditationDTO) {
        return modelMapper.map(accreditationDTO, Accreditation.class);
    }
}
