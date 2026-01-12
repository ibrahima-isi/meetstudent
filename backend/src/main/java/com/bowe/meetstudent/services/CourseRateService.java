package com.bowe.meetstudent.services;

import com.bowe.meetstudent.repositories.CourseRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseRateService {
    private final CourseRateRepository courseRateRepository;
}
