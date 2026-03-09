package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.repositories.CourseRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseRateService {
    private final CourseRateRepository courseRateRepository;

    @Transactional
    public CourseRate save(CourseRate courseRate) {
        return courseRateRepository.save(courseRate);
    }

    public List<CourseRate> findByCourseId(Integer courseId, org.springframework.data.domain.Sort sort) {
        return courseRateRepository.findByCourseId(courseId, sort);
    }

    public Double getAverageNoteByCourseId(Integer courseId) {
        return courseRateRepository.getAverageNoteByCourseId(courseId);
    }

    public Optional<CourseRate> findById(Integer id) {
        return courseRateRepository.findById(id);
    }

    public Page<CourseRate> findAll(Pageable pageable) {
        return courseRateRepository.findAll(pageable);
    }

    @Transactional
    public void delete(Integer id) {
        courseRateRepository.deleteById(id);
    }
}
