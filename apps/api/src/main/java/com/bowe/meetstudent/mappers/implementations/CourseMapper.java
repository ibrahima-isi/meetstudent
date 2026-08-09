package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseMapper implements Mapper<Course, CourseDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final MediaMapper mediaMapper;

    @Override
    public CourseDTO toDTO(Course course) {
        CourseDTO dto = modelMapper.map(course, CourseDTO.class);
        dto.setPhotoMediaId(course.getPhotoMediaId());
        dto.setPhoto(mediaService.findById(course.getPhotoMediaId()).map(mediaMapper::toDTO).orElse(null));
        return dto;
    }

    @Override
    public Course toEntity(CourseDTO courseDTO) {
        Course course = modelMapper.map(courseDTO, Course.class);
        course.setPhotoMediaId(courseDTO.getPhotoMediaId());
        if (courseDTO.getProgramId() == null) {
            course.setProgram(null);
        }
        return course;
    }
}
