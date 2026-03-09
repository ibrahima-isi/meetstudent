package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchoolMapper implements Mapper<School, SchoolDTO> {

    private final ModelMapper modelMapper;

    /**
     * Get a UserDTO from a UserEntity
     *
     * @param school the user entity to map from
     * @return UserDTO that have been mapped
     */
    @Override
    public SchoolDTO toDTO(School school) {
        return modelMapper.map(school, SchoolDTO.class);
    }

    /**
     * Get a UserEntity from a UserDTO
     *
     * @param schoolDTO the user DTO to map from
     * @return UserEntity that have been mapped
     */
    @Override
    public School toEntity(SchoolDTO schoolDTO) {
        School school = modelMapper.map(schoolDTO, School.class);
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
