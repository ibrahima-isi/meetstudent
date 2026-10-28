package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.repositories.ProgramRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.repositories.TagRepository;
import com.bowe.meetstudent.services.EntityImageValidator;
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

    @Mock
    private EntityImageValidator imageValidator;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ProgramRepository programRepository;

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
    void testDeleteClearsWishlistLinksBeforeDeletingTheSchool() {
        Program program = Program.builder().name("P").photoMediaId(20).build();
        program.setId(7);
        program.setCourses(new java.util.ArrayList<>(java.util.List.of(
                Course.builder().name("C1").photoMediaId(30).build(),
                Course.builder().name("C2").build(),
                Course.builder().name("C3").photoMediaId(31).build())));
        Program bare = Program.builder().name("B").build();
        bare.setId(8);
        school.setPrograms(new java.util.ArrayList<>(java.util.List.of(program, bare)));
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));

        schoolService.delete(1);

        var order = Mockito.inOrder(programRepository, schoolRepository, mediaService);
        order.verify(programRepository).deleteWishlistLinks(7);
        order.verify(programRepository).deleteWishlistLinks(8);
        order.verify(schoolRepository).deleteWishlistLinks(1);
        order.verify(schoolRepository).deleteById(1);
        order.verify(mediaService).deleteByIdsAfterCommit(java.util.Arrays.asList(10, 11, 20, 30, null, 31, null));
    }

    @Test
    void testDeleteNeverTouchesMediaWhenTheDeleteFails() {
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("fk"))
                .when(schoolRepository).deleteById(1);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> schoolService.delete(1));

        Mockito.verify(mediaService, Mockito.never()).deleteByIdsAfterCommit(any());
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
    void testPatchWithARefusedLogoKeepsEverythingAndDeletesNothing() {
        School updates = School.builder().logoMediaId(20).build();
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.doThrow(new com.bowe.meetstudent.exceptions.FieldValidationException("logoMediaId", "no"))
                .when(imageValidator).requireSchoolLogo(20, 10, 1);

        assertThrows(com.bowe.meetstudent.exceptions.FieldValidationException.class, () -> schoolService.patch(1, updates));

        assertEquals(10, school.getLogoMediaId());
        Mockito.verify(mediaService, Mockito.never()).deleteById(any());
        Mockito.verify(schoolRepository, Mockito.never()).save(any());
    }

    @Test
    void testSaveValidatesLogoCoverAndNestedPhotos() {
        Course course = Course.builder().name("C").photoMediaId(3).build();
        Program program = Program.builder().name("P").photoMediaId(2).courses(java.util.List.of(course)).build();
        School created = School.builder().name("S").logoMediaId(1).coverMediaId(4).programs(java.util.List.of(program)).build();
        Mockito.when(schoolRepository.save(created)).thenReturn(created);

        schoolService.save(created);

        Mockito.verify(imageValidator).requireSchoolLogo(1, null, null);
        Mockito.verify(imageValidator).requireSchoolCover(4, null, null);
        Mockito.verify(imageValidator).requireProgramPhoto(2, null, null);
        Mockito.verify(imageValidator).requireCoursePhoto(3, null, null);
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
