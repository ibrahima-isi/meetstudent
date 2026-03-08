package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramRateMapper implements Mapper<ProgramRate, ProgramRateDTO> {

    private final ModelMapper modelMapper;

    @Override
    public ProgramRateDTO toDTO(ProgramRate programRate) {
        return modelMapper.map(programRate, ProgramRateDTO.class);
    }

    @Override
    public ProgramRate toEntity(ProgramRateDTO programRateDTO) {
        return modelMapper.map(programRateDTO, ProgramRate.class);
    }
}
