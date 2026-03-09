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

    private void setupUser() {
        if (testUser == null) {
            Role role = roleService.createRole(Role.builder().name("ROLE_STUDENT").build());
            testUser = userService.saveUser(UserEntity.builder()
                    .firstname("Test")
                    .lastname("User")
                    .email("test@example.com")
                    .password("password")
                    .role(role)
                    .build(), passwordEncoder);
        }
    }

    @Test
    void testThatCreateSchoolRateReturnsHttpStatus201() throws Exception {
        setupUser();
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
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatGetRatesBySchoolReturnsList() throws Exception {
        setupUser();
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
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        );

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/school-rates/school/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$[0].comment").value("Great school")
        );
    }

    @Test
    void testThatSchoolDtoIncludesAverageRate() throws Exception {
        setupUser();
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        
        SchoolRateDTO rate1 = SchoolRateDTO.builder().note(5.0).schoolId(school.getId()).userId(testUser.getId()).build();
        SchoolRateDTO rate2 = SchoolRateDTO.builder().note(3.0).schoolId(school.getId()).userId(testUser.getId()).build();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(rate1)).with(TestDataUtil.mockUser("ROLE_STUDENT")));
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(rate2)).with(TestDataUtil.mockUser("ROLE_STUDENT")));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.averageRate").value(4.0)
        );
    }

    @Test
    void testThatGetSchoolRateByIdReturnsHttpStatus200() throws Exception {
        setupUser();
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        SchoolRateDTO rateDTO = SchoolRateDTO.builder().note(4.0).schoolId(school.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andReturn().getResponse().getContentAsString();
        
        SchoolRateDTO saved = objectMapper.readValue(response, SchoolRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/school-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.note").value(4.0)
        );
    }

    @Test
    void testThatUpdateSchoolRateReturnsHttpStatus200() throws Exception {
        setupUser();
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        SchoolRateDTO rateDTO = SchoolRateDTO.builder().note(4.0).schoolId(school.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andReturn().getResponse().getContentAsString();
        
        SchoolRateDTO saved = objectMapper.readValue(response, SchoolRateDTO.class);
        saved.setNote(5.0);

        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/v1/school-rates/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saved))
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.note").value(5.0)
        );
    }

    @Test
    void testThatDeleteSchoolRateReturnsHttpStatus200() throws Exception {
        setupUser();
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        SchoolRateDTO rateDTO = SchoolRateDTO.builder().note(4.0).schoolId(school.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andReturn().getResponse().getContentAsString();
        
        SchoolRateDTO saved = objectMapper.readValue(response, SchoolRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/school-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/school-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isNotFound()
        );
    }
}
