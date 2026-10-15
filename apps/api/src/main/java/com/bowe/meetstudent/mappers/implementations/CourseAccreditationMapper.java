package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.CourseAccreditationDTO;
import com.bowe.meetstudent.entities.CourseAccreditation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseAccreditationMapper {
    private final AccreditationMapper accreditationMapper;

    public CourseAccreditationDTO toDTO(CourseAccreditation link) {
        return CourseAccreditationDTO.builder()
                .courseId(link.getId().getCourseId())
                .accreditationId(link.getId().getAccreditationId())
                .accreditation(accreditationMapper.toDTO(link.getAccreditation()))
                .startsAt(link.getStartsAt())
                .endsAt(link.getEndsAt())
                .build();
    }
}
