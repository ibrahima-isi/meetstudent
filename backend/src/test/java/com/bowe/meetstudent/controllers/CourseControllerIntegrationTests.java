package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Course;
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
}
