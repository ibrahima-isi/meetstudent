package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.implementations.AccreditationMapper;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.services.AccreditationService;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A successful single-resource read must answer 200, never 302: browsers/HttpClient treat
 * a non-2xx status as an error, which made login impossible.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuccessfulReadStatusIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProgramService programService;
    @Autowired private ProgramMapper programMapper;
    @Autowired private CourseService courseService;
    @Autowired private CourseMapper courseMapper;
    @Autowired private AccreditationService accreditationService;
    @Autowired private AccreditationMapper accreditationMapper;

    private UserEntity student() {
        Role role = roleRepository.findByName("ROLE_STUDENT")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_STUDENT").build()));
        return userService.saveUser(UserEntity.builder()
                .firstname("Own").lastname("User").email("own.user@example.com")
                .password("password123").role(role).build(), passwordEncoder);
    }

    @Test
    void getUserByEmailReturns200() throws Exception {
        UserEntity user = student();
        mockMvc.perform(get("/api/v1/users/email/" + user.getEmail())
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void getUserByIdReturns200() throws Exception {
        UserEntity user = student();
        mockMvc.perform(get("/api/v1/users/id/" + user.getId())
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void getProgramByIdReturns200() throws Exception {
        var program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        mockMvc.perform(get("/api/v1/programs/" + program.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void getCourseByIdReturns200() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        mockMvc.perform(get("/api/v1/courses/" + course.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void getAccreditationByIdReturns200() throws Exception {
        var acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        mockMvc.perform(get("/api/v1/accreditations/" + acc.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void getRoleByIdReturns200() throws Exception {
        Role role = roleRepository.save(Role.builder().name("ROLE_MANAGER").build());
        mockMvc.perform(get("/api/v1/roles/" + role.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk());
    }
}
