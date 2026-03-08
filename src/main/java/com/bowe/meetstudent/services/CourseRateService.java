package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.repositories.CourseRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseRateService {
    private final CourseRateRepository courseRateRepository;

    public CourseRate save(CourseRate courseRate) {
        return courseRateRepository.save(courseRate);
    }

    public List<CourseRate> findByCourseId(Integer courseId) {
        return courseRateRepository.findByCourseId(courseId);
    }

    public Double getAverageNoteByCourseId(Integer courseId) {
        return courseRateRepository.getAverageNoteByCourseId(courseId);
    }
}
