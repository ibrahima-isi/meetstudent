package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchoolMapper implements Mapper<School, SchoolDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final MediaMapper mediaMapper;

    @Override
    public SchoolDTO toDTO(School school) {
        SchoolDTO dto = modelMapper.map(school, SchoolDTO.class);
        dto.setLogoMediaId(school.getLogoMediaId());
        dto.setCoverMediaId(school.getCoverMediaId());
        dto.setLogo(mediaService.findById(school.getLogoMediaId()).map(mediaMapper::toDTO).orElse(null));
        dto.setCover(mediaService.findById(school.getCoverMediaId()).map(mediaMapper::toDTO).orElse(null));
        return dto;
    }

    @Override
    public School toEntity(SchoolDTO schoolDTO) {
        School school = modelMapper.map(schoolDTO, School.class);
        school.setLogoMediaId(schoolDTO.getLogoMediaId());
        school.setCoverMediaId(schoolDTO.getCoverMediaId());
        if (school.getPrograms() != null) {
            school.getPrograms().forEach(p -> {
                p.setSchool(school);
                if (p.getCourses() != null) {
                    p.getCourses().forEach(c -> c.setProgram(p));
                }
            });
        }
        return school;
    }
}
