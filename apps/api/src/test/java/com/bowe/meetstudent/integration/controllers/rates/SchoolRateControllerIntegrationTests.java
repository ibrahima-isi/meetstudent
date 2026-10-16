package com.bowe.meetstudent.integration.controllers.rates;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.SchoolService;
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
class SchoolRateControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private SchoolMapper schoolMapper;

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
    void testThatCreateSchoolRateReturnsHttpStatus201() throws Exception {
        setupUser("ROLE_STUDENT");
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        
        SchoolRateDTO rateDTO = SchoolRateDTO.builder()
                .note(4.5)
                .comment("Great school")
                .schoolId(school.getId())
                .userId(testUser.getId())
                .build();

        String json = objectMapper.writeValueAsString(rateDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.userId").value(testUser.getId())
        );
    }

    @Test
    void testThatCreateSchoolRateRejectsAnotherUserId() throws Exception {
        setupUser("ROLE_STUDENT");
        UserEntity otherUser = createUser("ROLE_STUDENT", "other-student@example.com");
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));

        SchoolRateDTO rateDTO = SchoolRateDTO.builder()
                .note(4.5)
                .comment("Impersonation attempt")
                .schoolId(school.getId())
                .userId(otherUser.getId())
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatAnonymousCannotCreateSchoolRate() throws Exception {
        setupUser("ROLE_STUDENT");
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));

        SchoolRateDTO rateDTO = SchoolRateDTO.builder()
                .note(4.5)
                .comment("Anonymous attempt")
                .schoolId(school.getId())
                .userId(testUser.getId())
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
        ).andExpect(
                MockMvcResultMatchers.status().isUnauthorized()
        );
    }

    @Test
    void testThatGetRatesBySchoolReturnsList() throws Exception {
        setupUser("ROLE_STUDENT");
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        
        SchoolRateDTO rateDTO = SchoolRateDTO.builder()
                .note(4.5)
                .comment("Great school")
                .schoolId(school.getId())
                .userId(testUser.getId())
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        );

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/school-rates/school/" + school.getId())
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$[0].comment").value("Great school")
        );
    }

    @Test
    void testThatDeleteSchoolRateReturnsHttpStatus204() throws Exception {
        setupUser("ROLE_STUDENT");
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        SchoolRateDTO rateDTO = SchoolRateDTO.builder().note(4.0).schoolId(school.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andReturn().getResponse().getContentAsString();
        
        SchoolRateDTO saved = objectMapper.readValue(response, SchoolRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/school-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );
    }

    @Test
    void testThatStudentCannotDeleteSchoolRate() throws Exception {
        setupUser("ROLE_STUDENT");
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        SchoolRateDTO rateDTO = SchoolRateDTO.builder().note(4.0).schoolId(school.getId()).userId(testUser.getId()).build();

        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andReturn().getResponse().getContentAsString();

        SchoolRateDTO saved = objectMapper.readValue(response, SchoolRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/school-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatRatingTheSameSchoolTwiceUpdatesTheRatingInPlace() throws Exception {
        setupUser("ROLE_STUDENT");
        var target = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        var body = SchoolRateDTO.builder().note(2.0).comment("first").schoolId(target.getId()).userId(testUser.getId()).build();
        var first = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = objectMapper.readValue(first, SchoolRateDTO.class).getId();

        body.setNote(5.0);
        body.setComment("second");
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(MockMvcResultMatchers.status().isOk())
         .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(id))
         .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(5.0))
         .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("second"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/school-rates").with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1));
    }

    @Test
    void testThatTwoUsersCanEachRateTheSameSchool() throws Exception {
        setupUser("ROLE_STUDENT");
        UserEntity other = createUser("ROLE_STUDENT", "second-rater@example.com");
        var target = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        for (var user : new UserEntity[] {testUser, other}) {
            var body = SchoolRateDTO.builder().note(3.0).schoolId(target.getId()).userId(user.getId()).build();
            mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
            ).andExpect(MockMvcResultMatchers.status().isCreated());
        }
    }

    @Test
    void testThatPostingAnotherUsersRateIdCannotOverwriteThatRating() throws Exception {
        setupUser("ROLE_STUDENT");
        UserEntity other = createUser("ROLE_STUDENT", "thief@example.com");
        var target = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        var victimBody = SchoolRateDTO.builder().note(4.0).comment("victim").schoolId(target.getId()).userId(testUser.getId()).build();
        var created = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(victimBody)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        int victimId = objectMapper.readValue(created, SchoolRateDTO.class).getId();

        var attack = SchoolRateDTO.builder().id(victimId).note(1.0).comment("stolen").schoolId(target.getId()).userId(other.getId()).build();
        var attackResult = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attack)).with(TestDataUtil.mockUser(other.getId(), "ROLE_STUDENT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(victimId, objectMapper.readValue(attackResult, SchoolRateDTO.class).getId());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/school-rates/" + victimId).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(4.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("victim"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.userId").value(testUser.getId()));
    }

    @Test
    void testThatACommentLongerThanTheColumnIsRejectedWith400() throws Exception {
        setupUser("ROLE_STUDENT");
        var target = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        var body = SchoolRateDTO.builder().note(3.0).comment("x".repeat(151)).schoolId(target.getId()).userId(testUser.getId()).build();
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest())
         .andExpect(MockMvcResultMatchers.jsonPath("$.comment").exists());
    }
}
