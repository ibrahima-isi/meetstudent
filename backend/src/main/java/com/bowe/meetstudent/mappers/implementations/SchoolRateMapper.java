package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchoolRateMapper implements Mapper<SchoolRate, SchoolRateDTO> {

    private final ModelMapper modelMapper;

    @Override
    public SchoolRateDTO toDTO(SchoolRate schoolRate) {
        return modelMapper.map(schoolRate, SchoolRateDTO.class);
    }

    @Override
    public SchoolRate toEntity(SchoolRateDTO schoolRateDTO) {
        return modelMapper.map(schoolRateDTO, SchoolRate.class);
    }
}
