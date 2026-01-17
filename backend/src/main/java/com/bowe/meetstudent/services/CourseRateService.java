package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.repositories.CourseRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseRateService {
    private final CourseRateRepository courseRateRepository;

    public CourseRate save(CourseRate courseRate) {
        return courseRateRepository.save(courseRate);
    }
}
