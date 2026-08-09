package com.bowe.meetstudent.integration.controllers.rates;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.services.RoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class CourseRateControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserEntity testUser;

    private void setupUser(String roleName) {
        testUser = createUser(roleName, roleName.toLowerCase() + "@example.com");
    }

    private UserEntity createUser(String roleName, String email) {
        Role role = roleService.findRoleByName(roleName).orElseGet(() -> 
            roleService.createRole(Role.builder().name(roleName).build())
        );
        return userService.saveUser(UserEntity.builder()
                .firstname("Test")
                .lastname("User")
                .email(email)
                .password("password")
                .role(role)
                .build(), passwordEncoder);
    }

    @Test
    void testThatCreateCourseRateReturnsHttpStatus201() throws Exception {
        setupUser("ROLE_EXPERT");
        Course course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        
        CourseRateDTO rateDTO = CourseRateDTO.builder()
                .note(4.5)
                .comment("Great course")
                .courseId(course.getId())
                .userId(testUser.getId())
                .build();

        String json = objectMapper.writeValueAsString(rateDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.userId").value(testUser.getId())
        );
    }

    @Test
    void testThatCreateCourseRateRejectsAnotherUserId() throws Exception {
        setupUser("ROLE_EXPERT");
        UserEntity otherUser = createUser("ROLE_EXPERT", "other-expert@example.com");
        Course course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));

        CourseRateDTO rateDTO = CourseRateDTO.builder()
                .note(4.5)
                .comment("Impersonation attempt")
                .courseId(course.getId())
                .userId(otherUser.getId())
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatStudentCannotCreateCourseRate() throws Exception {
        setupUser("ROLE_STUDENT");
        Course course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        CourseRateDTO rateDTO = CourseRateDTO.builder()
                .note(4.5)
                .courseId(course.getId())
                .userId(testUser.getId())
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatGetCourseRateByIdReturnsHttpStatus200() throws Exception {
        setupUser("ROLE_EXPERT");
        Course course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        CourseRateDTO rateDTO = CourseRateDTO.builder().note(4.0).courseId(course.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andReturn().getResponse().getContentAsString();
        
        CourseRateDTO saved = objectMapper.readValue(response, CourseRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/course-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.note").value(4.0)
        );
    }

    @Test
    void testThatDeleteCourseRateReturnsHttpStatus204() throws Exception {
        setupUser("ROLE_EXPERT");
        Course course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        CourseRateDTO rateDTO = CourseRateDTO.builder().note(4.0).courseId(course.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andReturn().getResponse().getContentAsString();
        
        CourseRateDTO saved = objectMapper.readValue(response, CourseRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/course-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );
    }

    @Test
    void testThatExpertCannotDeleteCourseRate() throws Exception {
        setupUser("ROLE_EXPERT");
        Course course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        CourseRateDTO rateDTO = CourseRateDTO.builder().note(4.0).courseId(course.getId()).userId(testUser.getId()).build();

        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andReturn().getResponse().getContentAsString();

        CourseRateDTO saved = objectMapper.readValue(response, CourseRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/course-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }
}
