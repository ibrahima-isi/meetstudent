package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseMapper implements Mapper<Course, CourseDTO> {

    private final ModelMapper modelMapper;

    @Override
    public CourseDTO toDTO(Course course) {
        return modelMapper.map(course, CourseDTO.class);
    }

    @Override
    public Course toEntity(CourseDTO courseDTO) {
        Course course = modelMapper.map(courseDTO, Course.class);
        if (courseDTO.getProgramId() == null) {
            course.setProgram(null);
        }
        return course;
    }
}
