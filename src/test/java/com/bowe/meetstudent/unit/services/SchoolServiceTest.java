package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.SchoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private SchoolService schoolService;

    private School school;

    @BeforeEach
    void setUp() {
        school = new School();
        school.setId(1);
        school.setName("Test School");
        school.setLogoUrl("schools/logo.jpg");
        school.setCoverPhotoUrl("schools/cover.jpg");
    }

    @Test
    void testDelete() {
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.doNothing().when(schoolRepository).deleteById(1);

        schoolService.delete(1);

        Mockito.verify(mediaService).deleteMediaByUrl("schools/logo.jpg");
        Mockito.verify(mediaService).deleteMediaByUrl("schools/cover.jpg");
        Mockito.verify(schoolRepository).deleteById(1);
    }

    @Test
    void testPatch() {
        School updates = School.builder()
                .name("Updated School")
                .logoUrl("schools/new-logo.jpg")
                .build();
        
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.when(schoolRepository.save(any(School.class))).thenReturn(school);

        schoolService.patch(1, updates);

        assertEquals("Updated School", school.getName());
        assertEquals("schools/new-logo.jpg", school.getLogoUrl());
        Mockito.verify(mediaService).deleteOldMediaIfChanged("schools/logo.jpg", "schools/new-logo.jpg");
        Mockito.verify(mediaService).deleteOldMediaIfChanged("schools/cover.jpg", null);
    }
}
