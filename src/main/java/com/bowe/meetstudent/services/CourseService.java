package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.repositories.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final MediaService mediaService;

    @Transactional
    public Course save(Course course) {
        return this.courseRepository.save(course);
    }

    public Page<Course> findAll(Pageable pageable) {
        return this.courseRepository.findAll(pageable);
    }

    public Optional<Course> findById(int id) {
        return this.courseRepository.findById(id);
    }

    public Page<Course> findByName(String name, Pageable pageable) {
        return this.courseRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public boolean exists(int id) {
        return this.courseRepository.existsById(id);
    }

    @Transactional
    public void delete(int id) {
        this.courseRepository.findById(id).ifPresent(course -> {
            mediaService.deleteMediaByUrl(course.getPhotoUrl());
            this.courseRepository.deleteById(id);
        });
    }

    public Page<Course> findAllOrderByRateDesc(Pageable pageable) {
        return courseRepository.findAllByOrderByAverageRateDesc(pageable);
    }

    public Page<Course> findAllOrderByRateAsc(Pageable pageable) {
        return courseRepository.findAllByOrderByAverageRateAsc(pageable);
    }

    @Transactional
    public Course patch(Integer id, Course updates) {
        return courseRepository.findById(id).map(existing -> {
            mediaService.deleteOldMediaIfChanged(existing.getPhotoUrl(), updates.getPhotoUrl());
            
            if (updates.getName() != null) existing.setName(updates.getName());
            if (updates.getCode() != null) existing.setCode(updates.getCode());
            if (updates.getPhotoUrl() != null) existing.setPhotoUrl(updates.getPhotoUrl());
            if (updates.getProgram() != null) existing.setProgram(updates.getProgram());
            
            return courseRepository.save(existing);
        }).orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("Course not found"));
    }
}
