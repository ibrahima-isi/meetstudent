package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.repositories.CourseRepository;
import com.bowe.meetstudent.services.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseService courseService;

    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(1);
        course.setName("Java Basics");
        course.setCode("JV101");
    }

    @Test
    void testSave() {
        Mockito.when(courseRepository.save(any(Course.class))).thenReturn(course);
        
        Course saved = courseService.save(course);
        
        assertNotNull(saved);
        assertEquals("Java Basics", saved.getName());
        Mockito.verify(courseRepository, Mockito.times(1)).save(course);
    }

    @Test
    void testFindById() {
        Mockito.when(courseRepository.findById(1)).thenReturn(Optional.of(course));
        
        Optional<Course> found = courseService.findById(1);
        
        assertTrue(found.isPresent());
        assertEquals("JV101", found.get().getCode());
    }

    @Test
    void testFindAll() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Course> page = new PageImpl<>(List.of(course));
        
        Mockito.when(courseRepository.findAll(pageRequest)).thenReturn(page);
        
        Page<Course> result = courseService.findAll(pageRequest);
        
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testFindByName() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Course> page = new PageImpl<>(List.of(course));
        
        Mockito.when(courseRepository.findByNameContainingIgnoreCase("Java", pageRequest)).thenReturn(page);
        
        Page<Course> result = courseService.findByName("Java", pageRequest);
        
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testExists() {
        Mockito.when(courseRepository.existsById(1)).thenReturn(true);
        assertTrue(courseService.exists(1));
    }

    @Test
    void testDelete() {
        Mockito.doNothing().when(courseRepository).deleteById(1);
        courseService.delete(1);
        Mockito.verify(courseRepository, Mockito.times(1)).deleteById(1);
    }
}
