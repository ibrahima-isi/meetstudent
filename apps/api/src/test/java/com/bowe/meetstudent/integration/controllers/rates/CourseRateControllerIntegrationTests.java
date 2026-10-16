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

    @Test
    void testThatRatingTheSameCourseTwiceUpdatesTheRatingInPlace() throws Exception {
        setupUser("ROLE_EXPERT");
        var target = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var body = CourseRateDTO.builder().note(2.0).comment("first").courseId(target.getId()).userId(testUser.getId()).build();
        var first = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = objectMapper.readValue(first, CourseRateDTO.class).getId();

        body.setNote(5.0);
        body.setComment("second");
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isOk())
         .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(id))
         .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(5.0))
         .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("second"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/course-rates").with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1));
    }

    @Test
    void testThatTwoUsersCanEachRateTheSameCourse() throws Exception {
        setupUser("ROLE_EXPERT");
        UserEntity other = createUser("ROLE_EXPERT", "second-rater@example.com");
        var target = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        for (var user : new UserEntity[] {testUser, other}) {
            var body = CourseRateDTO.builder().note(3.0).courseId(target.getId()).userId(user.getId()).build();
            mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(user.getId(), "ROLE_EXPERT"))
            ).andExpect(MockMvcResultMatchers.status().isCreated());
        }
    }

    @Test
    void testThatPostingAnotherUsersRateIdCannotOverwriteThatRating() throws Exception {
        setupUser("ROLE_EXPERT");
        UserEntity other = createUser("ROLE_EXPERT", "thief@example.com");
        var target = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var victimBody = CourseRateDTO.builder().note(4.0).comment("victim").courseId(target.getId()).userId(testUser.getId()).build();
        var created = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(victimBody)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        int victimId = objectMapper.readValue(created, CourseRateDTO.class).getId();

        var attack = CourseRateDTO.builder().id(victimId).note(1.0).comment("stolen").courseId(target.getId()).userId(other.getId()).build();
        var attackResult = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attack)).with(TestDataUtil.mockUser(other.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(victimId, objectMapper.readValue(attackResult, CourseRateDTO.class).getId());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/course-rates/" + victimId).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(4.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("victim"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.userId").value(testUser.getId()));
    }

    @Test
    void testThatACommentLongerThanTheColumnIsRejectedWith400() throws Exception {
        setupUser("ROLE_EXPERT");
        var target = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var body = CourseRateDTO.builder().note(3.0).comment("x".repeat(151)).courseId(target.getId()).userId(testUser.getId()).build();
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest())
         .andExpect(MockMvcResultMatchers.jsonPath("$.comment").exists());
    }

    @Test
    void testThatReRatingWithoutACommentKeepsItButAnEmptyOneClearsIt() throws Exception {
        setupUser("ROLE_EXPERT");
        var target = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var auth = TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT");
        var body = CourseRateDTO.builder().note(2.0).comment("keep me").courseId(target.getId()).userId(testUser.getId()).build();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(auth)).andExpect(MockMvcResultMatchers.status().isCreated());

        body.setNote(4.0);
        body.setComment(null);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(auth))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(4.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("keep me"));

        body.setNote(5.0);
        body.setComment("");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(auth))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(5.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value(""));
    }

    @Test
    void reRatingChangesOnlyTheAuthorsCourseRatingAndRecomputesTheAverage() throws Exception {
        setupUser("ROLE_EXPERT");
        var other = createUser("ROLE_EXPERT", "independent-expert@example.com");
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var anotherDto = TestDataUtil.createCourseDto();
        anotherDto.setCode("OTHER");
        var anotherCourse = courseService.save(courseMapper.toEntity(anotherDto));
        var first = postRating(testUser, "ROLE_EXPERT", course.getId(), 2.0, 201);
        var independent = postRating(other, "ROLE_EXPERT", course.getId(), 3.0, 201);
        var elsewhere = postRating(testUser, "ROLE_EXPERT", anotherCourse.getId(), 1.0, 201);

        var updated = postRating(testUser, "ROLE_EXPERT", course.getId(), 5.0, 200);
        org.junit.jupiter.api.Assertions.assertEquals(first.getId(), updated.getId());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/course-rates/course/" + course.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/course-rates/" + independent.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.userId").value(other.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(3.0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/course-rates/" + elsewhere.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(1.0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/" + course.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.averageRate").value(4.0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/" + anotherCourse.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.averageRate").value(1.0));
    }

    @Test
    void unauthorizedRolesAndAnonymousCannotCreateOrReplaceACourseRating() throws Exception {
        setupUser("ROLE_EXPERT");
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var existing = postRating(testUser, "ROLE_EXPERT", course.getId(), 4.0, 201);
        for (var role : new String[] {"ROLE_STUDENT", "ROLE_ADMIN", "ANONYMOUS"}) {
            var body = CourseRateDTO.builder().id(existing.getId()).userId(testUser.getId())
                    .courseId(course.getId()).note(1.0).build();
            var request = MockMvcRequestBuilders.post("/api/v1/course-rates")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
            if (!role.equals("ANONYMOUS")) request.with(TestDataUtil.mockUser(testUser.getId(), role));
            mockMvc.perform(request).andExpect(MockMvcResultMatchers.status().is(role.equals("ANONYMOUS") ? 401 : 403));
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/course-rates/course/" + course.getId()))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                    .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(existing.getId()))
                    .andExpect(MockMvcResultMatchers.jsonPath("$[0].userId").value(testUser.getId()))
                    .andExpect(MockMvcResultMatchers.jsonPath("$[0].note").value(4.0));
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/" + course.getId()))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.averageRate").value(4.0));
        }
    }

    private CourseRateDTO postRating(UserEntity user, String role, Integer courseId, double note, int status) throws Exception {
        var body = CourseRateDTO.builder().courseId(courseId).userId(user.getId()).note(note).build();
        var response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
                        .with(TestDataUtil.mockUser(user.getId(), role)))
                .andExpect(MockMvcResultMatchers.status().is(status)).andReturn().getResponse();
        return objectMapper.readValue(response.getContentAsString(), CourseRateDTO.class);
    }

    @Test
    void testThatCreateCourseRateWithoutCourseIdIsRejectedWith400() throws Exception {
        setupUser("ROLE_EXPERT");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/course-rates").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"note\":3.0,\"userId\":" + testUser.getId() + "}")
                            .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
            ).andExpect(MockMvcResultMatchers.status().isBadRequest())
             .andExpect(MockMvcResultMatchers.jsonPath("$.courseId").value("Course ID is required"));
        }
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/course-rates").with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0));
    }
}
