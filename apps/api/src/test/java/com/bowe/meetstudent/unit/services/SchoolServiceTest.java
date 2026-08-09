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
        school.setLogoMediaId(10);
        school.setCoverMediaId(11);
    }

    @Test
    void testDeleteRemovesReferencedMedia() {
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.doNothing().when(schoolRepository).deleteById(1);

        schoolService.delete(1);

        Mockito.verify(mediaService).deleteById(10);
        Mockito.verify(mediaService).deleteById(11);
        Mockito.verify(schoolRepository).deleteById(1);
    }

    @Test
    void testPatchReplacesLogoMediaAndDeletesOld() {
        School updates = School.builder().name("Updated School").logoMediaId(20).build();

        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.when(schoolRepository.save(any(School.class))).thenReturn(school);

        schoolService.patch(1, updates);

        assertEquals("Updated School", school.getName());
        assertEquals(20, school.getLogoMediaId());
        Mockito.verify(mediaService).deleteById(10); // old logo removed
        Mockito.verify(mediaService, Mockito.never()).deleteById(11); // cover unchanged
    }

    @Test
    void testPatchWithSameLogoMediaIdDoesNotDelete() {
        School updates = School.builder().logoMediaId(10).build();

        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.when(schoolRepository.save(any(School.class))).thenReturn(school);

        schoolService.patch(1, updates);

        Mockito.verify(mediaService, Mockito.never()).deleteById(any());
    }
}
