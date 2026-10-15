package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.CourseAccreditation;
import com.bowe.meetstudent.entities.embedded.CourseAccreditationId;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.AccreditationRepository;
import com.bowe.meetstudent.repositories.CourseAccreditationRepository;
import com.bowe.meetstudent.repositories.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseAccreditationService {
    private final CourseRepository courseRepository;
    private final AccreditationRepository accreditationRepository;
    private final CourseAccreditationRepository courseAccreditationRepository;

    public Page<CourseAccreditation> findByCourseId(Integer courseId, Pageable pageable) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Cours non trouvé");
        }
        return courseAccreditationRepository.findByCourseId(courseId, pageable);
    }

    public Optional<CourseAccreditation> findById(Integer courseId, Integer accreditationId) {
        return courseAccreditationRepository.findById(new CourseAccreditationId(courseId, accreditationId));
    }

    @Transactional
    public CourseAccreditation add(Integer courseId, Integer accreditationId, Integer startsAt, Integer endsAt) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cours non trouvé"));
        Accreditation accreditation = accreditationRepository.findById(accreditationId)
                .orElseThrow(() -> new ResourceNotFoundException("Accréditation non trouvée"));
        CourseAccreditationId id = new CourseAccreditationId(courseId, accreditationId);
        if (courseAccreditationRepository.existsById(id)) {
            throw new ConflictException("This accreditation is already linked to the course");
        }
        CourseAccreditation link = new CourseAccreditation(id, course, accreditation, startsAt, endsAt);
        return courseAccreditationRepository.save(link);
    }

    @Transactional
    public void remove(Integer courseId, Integer accreditationId) {
        CourseAccreditationId id = new CourseAccreditationId(courseId, accreditationId);
        CourseAccreditation link = courseAccreditationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Association non trouvée"));
        courseAccreditationRepository.delete(link);
    }
}
