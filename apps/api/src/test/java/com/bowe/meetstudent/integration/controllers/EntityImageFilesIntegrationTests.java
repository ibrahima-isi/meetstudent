package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.repositories.CourseRepository;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.repositories.ProgramRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.services.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entity images (school logo/cover, program and course photos): the stored file must outlive a
 * failed request and go away only once the database change is committed. Nothing here is
 * transactional, so every request commits (or rolls back) for real.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntityImageFilesIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private MediaRepository mediaRepository;
    @Autowired private MediaStorageService storage;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private CourseRepository courseRepository;

    /** A media row backed by a real file on disk. */
    private Media image(MediaCategory category) throws IOException {
        String key = storage.store(new byte[] {1, 2, 3}, "png", MediaVisibility.PUBLIC);
        return mediaRepository.save(Media.builder().storageKey(key).originalFilename("i.png")
                .contentType("image/png").sizeBytes(3L).category(category)
                .visibility(MediaVisibility.PUBLIC).build());
    }

    private boolean fileExists(Media media) {
        try {
            return storage.loadAsResource(media.getStorageKey()).exists();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean rowExists(Media media) {
        return mediaRepository.findById(media.getId()).isPresent();
    }

    private School school(String code, Media logo) {
        return schoolRepository.save(School.builder().name("School " + code).code(code)
                .logoMediaId(logo == null ? null : logo.getId()).build());
    }

    private void putSchool(int id, String json, int expectedStatus) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/schools/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().is(expectedStatus));
    }

    // --- replacement ---

    @Test
    void failedSchoolReplacementKeepsTheOldLogoFileAndRow() throws Exception {
        Media oldLogo = image(MediaCategory.SCHOOL_LOGO);
        Media newLogo = image(MediaCategory.SCHOOL_LOGO);
        School target = school("AAA", oldLogo);
        school("BBB", null);

        putSchool(target.getId(), "{\"code\":\"BBB\",\"logoMediaId\":" + newLogo.getId() + "}", 409);

        assertTrue(fileExists(oldLogo), "old logo file must survive the rollback");
        assertTrue(rowExists(oldLogo));
        assertTrue(fileExists(newLogo));
        assertEquals(oldLogo.getId(), schoolRepository.findById(target.getId()).orElseThrow().getLogoMediaId());
    }

    @Test
    void successfulSchoolReplacementRemovesTheOldLogoFileAndRow() throws Exception {
        Media oldLogo = image(MediaCategory.SCHOOL_LOGO);
        Media newLogo = image(MediaCategory.SCHOOL_LOGO);
        School target = school("AAA", oldLogo);

        putSchool(target.getId(), "{\"logoMediaId\":" + newLogo.getId() + "}", 200);

        assertFalse(fileExists(oldLogo), "old logo file is removed once committed");
        assertFalse(rowExists(oldLogo));
        assertTrue(fileExists(newLogo));
        assertTrue(rowExists(newLogo));
    }

    @Test
    void successfulProgramReplacementRemovesTheOldPhotoFileAfterCommit() throws Exception {
        Media oldPhoto = image(MediaCategory.PROGRAM_PHOTO);
        Media newPhoto = image(MediaCategory.PROGRAM_PHOTO);
        Program program = programRepository.save(Program.builder().name("P").code("PPP")
                .photoMediaId(oldPhoto.getId()).build());

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/programs/" + program.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"photoMediaId\":" + newPhoto.getId() + "}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertFalse(fileExists(oldPhoto));
        assertFalse(rowExists(oldPhoto));
        assertTrue(fileExists(newPhoto));
    }

    @Test
    void successfulCourseReplacementRemovesTheOldPhotoFileAfterCommit() throws Exception {
        Media oldPhoto = image(MediaCategory.COURSE_PHOTO);
        Media newPhoto = image(MediaCategory.COURSE_PHOTO);
        Course course = courseRepository.save(Course.builder().name("C").code("CCC")
                .photoMediaId(oldPhoto.getId()).build());

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/courses/" + course.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"photoMediaId\":" + newPhoto.getId() + "}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertFalse(fileExists(oldPhoto));
        assertFalse(rowExists(oldPhoto));
        assertTrue(fileExists(newPhoto));
    }

    // --- deletion ---

    @Test
    void deletingASchoolRemovesItsProgramAndCoursePhotosToo() throws Exception {
        Media logo = image(MediaCategory.SCHOOL_LOGO);
        Media cover = image(MediaCategory.SCHOOL_COVER);
        Media programPhoto = image(MediaCategory.PROGRAM_PHOTO);
        Media coursePhoto = image(MediaCategory.COURSE_PHOTO);
        Media unrelated = image(MediaCategory.SCHOOL_LOGO);
        School school = schoolRepository.save(School.builder().name("S").code("SSS")
                .logoMediaId(logo.getId()).coverMediaId(cover.getId()).build());
        Program program = programRepository.save(Program.builder().name("P").code("PPP")
                .school(school).photoMediaId(programPhoto.getId()).build());
        courseRepository.save(Course.builder().name("C").code("CCC").program(program)
                .photoMediaId(coursePhoto.getId()).build());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        for (Media gone : List.of(logo, cover, programPhoto, coursePhoto)) {
            assertFalse(rowExists(gone), "row of " + gone.getCategory());
            assertFalse(fileExists(gone), "file of " + gone.getCategory());
        }
        assertTrue(rowExists(unrelated));
        assertTrue(fileExists(unrelated));
    }

    @Test
    void deletingAProgramRemovesItsCoursesPhotosToo() throws Exception {
        Media programPhoto = image(MediaCategory.PROGRAM_PHOTO);
        Media coursePhoto = image(MediaCategory.COURSE_PHOTO);
        Media unrelated = image(MediaCategory.COURSE_PHOTO);
        Program program = programRepository.save(Program.builder().name("P").code("PPP")
                .photoMediaId(programPhoto.getId()).build());
        courseRepository.save(Course.builder().name("C").code("CCC").program(program)
                .photoMediaId(coursePhoto.getId()).build());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/programs/" + program.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        for (Media gone : List.of(programPhoto, coursePhoto)) {
            assertFalse(rowExists(gone));
            assertFalse(fileExists(gone));
        }
        assertTrue(rowExists(unrelated));
        assertTrue(fileExists(unrelated));
    }

    @Test
    void deletingACourseRemovesItsPhotoFileAndRow() throws Exception {
        Media photo = image(MediaCategory.COURSE_PHOTO);
        Course course = courseRepository.save(Course.builder().name("C").code("CCC")
                .photoMediaId(photo.getId()).build());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/courses/" + course.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertFalse(rowExists(photo));
        assertFalse(fileExists(photo));
    }

    @Test
    void aStudentCannotDeleteASchoolAndNothingIsTouched() throws Exception {
        Media logo = image(MediaCategory.SCHOOL_LOGO);
        School school = school("AAA", logo);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + school.getId())
                        .with(TestDataUtil.mockUser(5, "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        assertTrue(rowExists(logo));
        assertTrue(fileExists(logo));
    }
}
