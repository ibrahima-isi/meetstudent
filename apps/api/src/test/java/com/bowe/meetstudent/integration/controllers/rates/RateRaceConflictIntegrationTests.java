package com.bowe.meetstudent.integration.controllers.rates;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.CourseRateService;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.ProgramRateService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.SchoolRateService;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** A lost insert race (the lookup saw nothing, the insert hit the unique constraint) must answer 409. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateRaceConflictIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private RoleService roleService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolMapper schoolMapper;
    @Autowired private ProgramService programService;
    @Autowired private ProgramMapper programMapper;
    @Autowired private CourseService courseService;
    @Autowired private CourseMapper courseMapper;

    @MockitoSpyBean private SchoolRateService schoolRateService;
    @MockitoSpyBean private ProgramRateService programRateService;
    @MockitoSpyBean private CourseRateService courseRateService;

    private UserEntity user(String roleName) {
        Role role = roleService.findRoleByName(roleName).orElseGet(() ->
                roleService.createRole(Role.builder().name(roleName).build()));
        return userService.saveUser(UserEntity.builder().firstname("T").lastname("U")
                .email("race@example.com").password("password").role(role)
                .expertStatus("ROLE_EXPERT".equals(roleName) ? ExpertStatus.APPROVED : null).build(), passwordEncoder);
    }

    private static DataIntegrityViolationException race(String constraint) {
        var hibernate = new ConstraintViolationException("could not execute statement",
                new SQLException("duplicate key", "23505"), constraint);
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }

    @Test
    void schoolRateRaceAnswers409() throws Exception {
        var u = user("ROLE_STUDENT");
        var target = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        doThrow(race("uk_school_rates_user_school")).when(schoolRateService).save(any(SchoolRate.class));
        var body = SchoolRateDTO.builder().note(3.0).schoolId(target.getId()).userId(u.getId()).build();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(u.getId(), "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void programRateRaceAnswers409() throws Exception {
        var u = user("ROLE_EXPERT");
        var target = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        doThrow(race("uk_program_rates_user_program")).when(programRateService).save(any(ProgramRate.class));
        var body = ProgramRateDTO.builder().note(3.0).programId(target.getId()).userId(u.getId()).build();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(u.getId(), "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void courseRateRaceAnswers409() throws Exception {
        var u = user("ROLE_EXPERT");
        var target = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        doThrow(race("uk_course_rates_user_course")).when(courseRateService).save(any(CourseRate.class));
        var body = CourseRateDTO.builder().note(3.0).courseId(target.getId()).userId(u.getId()).build();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(u.getId(), "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }
}
