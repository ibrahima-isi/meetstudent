package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseRateMapper implements Mapper<CourseRate, CourseRateDTO> {

    private final ModelMapper modelMapper;

    @Override
    public CourseRateDTO toDTO(CourseRate courseRate) {
        return modelMapper.map(courseRate, CourseRateDTO.class);
    }

    @Override
    public CourseRate toEntity(CourseRateDTO courseRateDTO) {
        return modelMapper.map(courseRateDTO, CourseRate.class);
    }
}
