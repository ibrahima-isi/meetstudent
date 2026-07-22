package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.ProgramService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseService courseService;

    @Autowired
    private ProgramService programService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private com.bowe.meetstudent.repositories.MediaRepository mediaRepository;

    private com.bowe.meetstudent.entities.Media persistedCoursePhoto() {
        var media = com.bowe.meetstudent.entities.Media.builder()
                .storageKey("public/course-it.png")
                .originalFilename("c.png").contentType("image/png").sizeBytes(10L)
                .category(com.bowe.meetstudent.entities.enums.MediaCategory.COURSE_PHOTO)
                .visibility(com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC)
                .build();
        return mediaRepository.save(media);
    }

    @Test
    void deleteCourseReturns200WithDeletedDto() throws Exception {
        com.bowe.meetstudent.entities.Course course =
                courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/courses/" + course.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(course.getId()));

        org.junit.jupiter.api.Assertions.assertTrue(courseService.findById(course.getId()).isEmpty());
    }

    @Test
    void putCourseWithPhotoMediaIdResolvesPhotoWithPublicUrl() throws Exception {
        com.bowe.meetstudent.entities.Course saved =
                courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var media = persistedCoursePhoto();

        CourseDTO body = TestDataUtil.createCourseDto();
        body.setPhotoMediaId(media.getId());
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/courses/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photo.id").value(media.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.photo.publicUrl").value("/uploads/public/course-it.png"));
    }

    @Test
    void testThatCreateCourseReturnsHttpStatus201Created() throws Exception {
        CourseDTO courseDTO = TestDataUtil.createCourseDto();

        String json = objectMapper.writeValueAsString(courseDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatCourseCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        CourseDTO courseDTO = TestDataUtil.createCourseDto();

        String json = objectMapper.writeValueAsString(courseDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(courseDTO.getName())
        );
    }

    @Test
    void testThatCreateCourseWithProgramReturnsHttpStatus201() throws Exception {
        Program program = new Program();
        program.setName("Test Program");
        program.setCode("TPRG1");
        program = programService.save(program);

        CourseDTO courseDTO = TestDataUtil.createCourseDto();
        courseDTO.setProgramId(program.getId());

        String json = objectMapper.writeValueAsString(courseDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(courseDTO.getName())
        );
    }

    @Test
    void testThatGetAllCoursesReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatGetAllCoursesReturnsAListOfCourses() throws Exception {
        CourseDTO courseDTO0 = TestDataUtil.createCourseDto();
        CourseDTO courseDTO1 = TestDataUtil.createCourseDto();
        courseDTO1.setCode("CRS01");

        this.courseService.save(this.courseMapper.toEntity(courseDTO0));
        this.courseService.save(this.courseMapper.toEntity(courseDTO1));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].id").isNumber()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[1].id").isNumber()
        );
    }

    @Test
    void testThatGetCoursesSortedByRateReturnsCorrectOrder() throws Exception {
        CourseDTO c1 = TestDataUtil.createCourseDto();
        c1.setName("Best Course");
        c1.setCode("BC001");
        com.bowe.meetstudent.entities.Course course1 = courseService.save(courseMapper.toEntity(c1));

        CourseDTO c2 = TestDataUtil.createCourseDto();
        c2.setName("Average Course");
        c2.setCode("AC001");
        com.bowe.meetstudent.entities.Course course2 = courseService.save(courseMapper.toEntity(c2));
        
        // Rate course1 with 5.0, course2 with 3.0
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": 5.0, \"courseId\": " + course1.getId() + ", \"userId\": 1}")
                .with(TestDataUtil.mockUser("ROLE_STUDENT")));
        
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": 3.0, \"courseId\": " + course2.getId() + ", \"userId\": 1}")
                .with(TestDataUtil.mockUser("ROLE_STUDENT")));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/courses?sortRate=most")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].id").value(course1.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[1].id").value(course2.getId())
        );
    }
}
