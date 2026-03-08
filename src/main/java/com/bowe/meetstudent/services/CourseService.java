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
        this.courseRepository.deleteById(id);
    }
}
