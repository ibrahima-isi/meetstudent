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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An entity image (school logo/cover, program or course photo) must be an ownerless, public
 * media of the matching category, not already used by another entity. Otherwise a later
 * replacement or deletion would destroy somebody else's document.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EntityImageCategoryIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MediaRepository mediaRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private CourseRepository courseRepository;

    private int seq = 0;

    private Media media(MediaCategory category, Integer ownerId) {
        return mediaRepository.save(Media.builder().storageKey("k/" + (++seq) + ".png")
                .originalFilename("i.png").contentType("image/png").sizeBytes(3L)
                .category(category).visibility(category.getVisibility())
                .ownerId(ownerId).build());
    }

    private Media media(MediaCategory category) {
        return media(category, null);
    }

    private String code() {
        return "C" + (++seq) + "X";
    }

    private School school(Media logo, Media cover) {
        return schoolRepository.save(School.builder().name("S" + seq).code(code())
                .logoMediaId(logo == null ? null : logo.getId())
                .coverMediaId(cover == null ? null : cover.getId()).build());
    }

    private Program program(Media photo) {
        return programRepository.save(Program.builder().name("P" + seq).code(code())
                .photoMediaId(photo == null ? null : photo.getId()).build());
    }

    private Course course(Media photo) {
        return courseRepository.save(Course.builder().name("K" + seq).code(code())
                .photoMediaId(photo == null ? null : photo.getId()).build());
    }

    private ResultActions send(HttpMethod method, String url, String json) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.request(method, url)
                .contentType(MediaType.APPLICATION_JSON).content(json)
                .with(TestDataUtil.mockUser("ROLE_ADMIN")));
    }

    private void putRejected(String url, String field, Media media) throws Exception {
        send(HttpMethod.PUT, url, "{\"" + field + "\":" + media.getId() + "}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$." + field).exists());
    }

    private void assertUntouched(Media media) {
        assertTrue(mediaRepository.findById(media.getId()).isPresent(), "media row must survive");
    }

    // --- school logo ---

    @Test
    void logoPointingToADiplomaIsRefusedAndTheDiplomaIsUntouched() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        School target = school(null, null);

        putRejected("/api/v1/schools/" + target.getId(), "logoMediaId", diploma);

        assertUntouched(diploma);
        assertNull(schoolRepository.findById(target.getId()).orElseThrow().getLogoMediaId());
    }

    @Test
    void replacingALogoWithADiplomaKeepsTheOldLogoAndTheDiploma() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        Media oldLogo = media(MediaCategory.SCHOOL_LOGO);
        School target = school(oldLogo, null);

        putRejected("/api/v1/schools/" + target.getId(), "logoMediaId", diploma);

        assertUntouched(diploma);
        assertUntouched(oldLogo);
        assertEquals(oldLogo.getId(), schoolRepository.findById(target.getId()).orElseThrow().getLogoMediaId());
    }

    @Test
    void logoPointingToAUserPhotoIsRefused() throws Exception {
        Media photo = media(MediaCategory.USER_PHOTO, 42);
        putRejected("/api/v1/schools/" + school(null, null).getId(), "logoMediaId", photo);
        assertUntouched(photo);
    }

    @Test
    void logoPointingToASchoolCoverIsRefused() throws Exception {
        putRejected("/api/v1/schools/" + school(null, null).getId(), "logoMediaId", media(MediaCategory.SCHOOL_COVER));
    }

    @Test
    void logoPointingToAnUnknownMediaIsRefused() throws Exception {
        send(HttpMethod.PUT, "/api/v1/schools/" + school(null, null).getId(), "{\"logoMediaId\":987654}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoMediaId").exists());
    }

    @Test
    void logoOfTheRightCategoryButOwnedByAUserIsRefused() throws Exception {
        putRejected("/api/v1/schools/" + school(null, null).getId(), "logoMediaId", media(MediaCategory.SCHOOL_LOGO, 42));
    }

    @Test
    void logoOfTheRightCategoryButPrivateIsRefused() throws Exception {
        Media priv = mediaRepository.save(Media.builder().storageKey("k/p.png").originalFilename("i.png")
                .contentType("image/png").sizeBytes(3L).category(MediaCategory.SCHOOL_LOGO)
                .visibility(MediaVisibility.PRIVATE).build());
        putRejected("/api/v1/schools/" + school(null, null).getId(), "logoMediaId", priv);
    }

    @Test
    void logoAlreadyUsedByAnotherSchoolIsRefused() throws Exception {
        Media shared = media(MediaCategory.SCHOOL_LOGO);
        school(shared, null);
        putRejected("/api/v1/schools/" + school(null, null).getId(), "logoMediaId", shared);
        assertUntouched(shared);
    }

    @Test
    void correctLogoIsAccepted() throws Exception {
        Media logo = media(MediaCategory.SCHOOL_LOGO);
        School target = school(null, null);

        send(HttpMethod.PUT, "/api/v1/schools/" + target.getId(), "{\"logoMediaId\":" + logo.getId() + "}")
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoMediaId").value(logo.getId()));
    }

    @Test
    void sendingTheCurrentLogoAgainStillWorks() throws Exception {
        Media logo = media(MediaCategory.SCHOOL_LOGO);
        School target = school(logo, null);

        send(HttpMethod.PUT, "/api/v1/schools/" + target.getId(),
                "{\"name\":\"Renamed\",\"logoMediaId\":" + logo.getId() + "}")
                .andExpect(MockMvcResultMatchers.status().isOk());
        assertUntouched(logo);
    }

    @Test
    void omittingTheLogoKeepsIt() throws Exception {
        Media logo = media(MediaCategory.SCHOOL_LOGO);
        School target = school(logo, null);

        send(HttpMethod.PATCH, "/api/v1/schools/" + target.getId(), "{\"name\":\"Renamed\"}")
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoMediaId").value(logo.getId()));
    }

    @Test
    void creatingASchoolWithADiplomaAsLogoIsRefused() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        var dto = TestDataUtil.createSchoolDto();
        dto.setLogoMediaId(diploma.getId());

        send(HttpMethod.POST, "/api/v1/schools", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoMediaId").exists());
        assertUntouched(diploma);
    }

    @Test
    void creatingASchoolWithACorrectLogoIsAccepted() throws Exception {
        Media logo = media(MediaCategory.SCHOOL_LOGO);
        var dto = TestDataUtil.createSchoolDto();
        dto.setLogoMediaId(logo.getId());

        send(HttpMethod.POST, "/api/v1/schools", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void creatingASchoolWithANestedProgramPhotoThatIsADiplomaIsRefused() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        var programDto = TestDataUtil.createProgramDto();
        programDto.setPhotoMediaId(diploma.getId());
        var dto = TestDataUtil.createSchoolDto();
        dto.setPrograms(List.of(programDto));

        send(HttpMethod.POST, "/api/v1/schools", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
        assertUntouched(diploma);
    }

    @Test
    void creatingASchoolWithANestedCoursePhotoThatIsADiplomaIsRefused() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        var courseDto = TestDataUtil.createCourseDto();
        courseDto.setPhotoMediaId(diploma.getId());
        var programDto = TestDataUtil.createProgramDto();
        programDto.setCourses(List.of(courseDto));
        var dto = TestDataUtil.createSchoolDto();
        dto.setPrograms(List.of(programDto));

        send(HttpMethod.POST, "/api/v1/schools", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
        assertUntouched(diploma);
    }

    @Test
    void creatingASchoolWithTwoNestedProgramsSharingAPhotoIsRefused() throws Exception {
        Media shared = media(MediaCategory.PROGRAM_PHOTO);
        var p1 = TestDataUtil.createProgramDto();
        p1.setPhotoMediaId(shared.getId());
        var p2 = TestDataUtil.createProgramDto();
        p2.setPhotoMediaId(shared.getId());
        var dto = TestDataUtil.createSchoolDto();
        dto.setPrograms(List.of(p1, p2));

        send(HttpMethod.POST, "/api/v1/schools", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
        assertUntouched(shared);
    }

    @Test
    void creatingASchoolWithDistinctNestedPhotosIsAccepted() throws Exception {
        var p1 = TestDataUtil.createProgramDto();
        p1.setPhotoMediaId(media(MediaCategory.PROGRAM_PHOTO).getId());
        var c1 = TestDataUtil.createCourseDto();
        c1.setPhotoMediaId(media(MediaCategory.COURSE_PHOTO).getId());
        p1.setCourses(List.of(c1));
        var p2 = TestDataUtil.createProgramDto();
        p2.setPhotoMediaId(media(MediaCategory.PROGRAM_PHOTO).getId());
        var dto = TestDataUtil.createSchoolDto();
        dto.setPrograms(List.of(p1, p2));

        send(HttpMethod.POST, "/api/v1/schools", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    // --- nested courses of POST /programs ---

    @Test
    void creatingAProgramWithANestedCoursePhotoThatIsADiplomaIsRefused() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        var courseDto = TestDataUtil.createCourseDto();
        courseDto.setPhotoMediaId(diploma.getId());
        var dto = TestDataUtil.createProgramDto();
        dto.setCourses(List.of(courseDto));

        send(HttpMethod.POST, "/api/v1/programs", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
        assertUntouched(diploma);
    }

    @Test
    void creatingAProgramWithTwoNestedCoursesSharingAPhotoIsRefused() throws Exception {
        Media shared = media(MediaCategory.COURSE_PHOTO);
        var c1 = TestDataUtil.createCourseDto();
        c1.setPhotoMediaId(shared.getId());
        var c2 = TestDataUtil.createCourseDto();
        c2.setPhotoMediaId(shared.getId());
        var dto = TestDataUtil.createProgramDto();
        dto.setCourses(List.of(c1, c2));

        send(HttpMethod.POST, "/api/v1/programs", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
    }

    @Test
    void creatingAProgramWithCorrectDistinctNestedCoursePhotosIsAccepted() throws Exception {
        var c1 = TestDataUtil.createCourseDto();
        c1.setPhotoMediaId(media(MediaCategory.COURSE_PHOTO).getId());
        var c2 = TestDataUtil.createCourseDto();
        c2.setPhotoMediaId(media(MediaCategory.COURSE_PHOTO).getId());
        var dto = TestDataUtil.createProgramDto();
        dto.setCourses(List.of(c1, c2));

        send(HttpMethod.POST, "/api/v1/programs", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    // --- school cover ---

    @Test
    void coverPointingToADiplomaIsRefused() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        putRejected("/api/v1/schools/" + school(null, null).getId(), "coverMediaId", diploma);
        assertUntouched(diploma);
    }

    @Test
    void coverPointingToASchoolLogoIsRefused() throws Exception {
        putRejected("/api/v1/schools/" + school(null, null).getId(), "coverMediaId", media(MediaCategory.SCHOOL_LOGO));
    }

    @Test
    void coverPointingToAnUnknownMediaIsRefused() throws Exception {
        send(HttpMethod.PATCH, "/api/v1/schools/" + school(null, null).getId(), "{\"coverMediaId\":987654}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.coverMediaId").exists());
    }

    @Test
    void correctCoverIsAccepted() throws Exception {
        Media cover = media(MediaCategory.SCHOOL_COVER);
        send(HttpMethod.PUT, "/api/v1/schools/" + school(null, null).getId(), "{\"coverMediaId\":" + cover.getId() + "}")
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.coverMediaId").value(cover.getId()));
    }

    // --- program photo ---

    @Test
    void programPhotoPointingToADiplomaIsRefusedAndTheDiplomaIsUntouched() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        putRejected("/api/v1/programs/" + program(null).getId(), "photoMediaId", diploma);
        assertUntouched(diploma);
    }

    @Test
    void programPhotoPointingToACoursePhotoIsRefused() throws Exception {
        putRejected("/api/v1/programs/" + program(null).getId(), "photoMediaId", media(MediaCategory.COURSE_PHOTO));
    }

    @Test
    void programPhotoPointingToAUserPhotoIsRefused() throws Exception {
        putRejected("/api/v1/programs/" + program(null).getId(), "photoMediaId", media(MediaCategory.USER_PHOTO, 42));
    }

    @Test
    void programPhotoPointingToAnUnknownMediaIsRefused() throws Exception {
        send(HttpMethod.PATCH, "/api/v1/programs/" + program(null).getId(), "{\"photoMediaId\":987654}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
    }

    @Test
    void programPhotoAlreadyUsedByAnotherProgramIsRefused() throws Exception {
        Media shared = media(MediaCategory.PROGRAM_PHOTO);
        program(shared);
        putRejected("/api/v1/programs/" + program(null).getId(), "photoMediaId", shared);
        assertUntouched(shared);
    }

    @Test
    void correctProgramPhotoIsAccepted() throws Exception {
        Media photo = media(MediaCategory.PROGRAM_PHOTO);
        send(HttpMethod.PUT, "/api/v1/programs/" + program(null).getId(), "{\"photoMediaId\":" + photo.getId() + "}")
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").value(photo.getId()));
    }

    @Test
    void creatingAProgramWithADiplomaAsPhotoIsRefused() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        var dto = TestDataUtil.createProgramDto();
        dto.setPhotoMediaId(diploma.getId());

        send(HttpMethod.POST, "/api/v1/programs", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
        assertUntouched(diploma);
    }

    @Test
    void creatingAProgramWithACorrectPhotoIsAccepted() throws Exception {
        Media photo = media(MediaCategory.PROGRAM_PHOTO);
        var dto = TestDataUtil.createProgramDto();
        dto.setPhotoMediaId(photo.getId());

        send(HttpMethod.POST, "/api/v1/programs", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    // --- course photo ---

    @Test
    void coursePhotoPointingToADiplomaIsRefusedAndTheDiplomaIsUntouched() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        putRejected("/api/v1/courses/" + course(null).getId(), "photoMediaId", diploma);
        assertUntouched(diploma);
    }

    @Test
    void coursePhotoPointingToAProgramPhotoIsRefused() throws Exception {
        putRejected("/api/v1/courses/" + course(null).getId(), "photoMediaId", media(MediaCategory.PROGRAM_PHOTO));
    }

    @Test
    void coursePhotoPointingToAnUnknownMediaIsRefused() throws Exception {
        send(HttpMethod.PATCH, "/api/v1/courses/" + course(null).getId(), "{\"photoMediaId\":987654}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
    }

    @Test
    void coursePhotoAlreadyUsedByAnotherCourseIsRefused() throws Exception {
        Media shared = media(MediaCategory.COURSE_PHOTO);
        course(shared);
        putRejected("/api/v1/courses/" + course(null).getId(), "photoMediaId", shared);
        assertUntouched(shared);
    }

    @Test
    void correctCoursePhotoIsAccepted() throws Exception {
        Media photo = media(MediaCategory.COURSE_PHOTO);
        send(HttpMethod.PUT, "/api/v1/courses/" + course(null).getId(), "{\"photoMediaId\":" + photo.getId() + "}")
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").value(photo.getId()));
    }

    @Test
    void creatingACourseWithADiplomaAsPhotoIsRefused() throws Exception {
        Media diploma = media(MediaCategory.DIPLOMA, 42);
        var dto = TestDataUtil.createCourseDto();
        dto.setPhotoMediaId(diploma.getId());

        send(HttpMethod.POST, "/api/v1/courses", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photoMediaId").exists());
        assertUntouched(diploma);
    }

    @Test
    void creatingACourseWithACorrectPhotoIsAccepted() throws Exception {
        Media photo = media(MediaCategory.COURSE_PHOTO);
        var dto = TestDataUtil.createCourseDto();
        dto.setPhotoMediaId(photo.getId());

        send(HttpMethod.POST, "/api/v1/courses", objectMapper.writeValueAsString(dto))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }
}
