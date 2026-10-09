package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestValidationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolMapper schoolMapper;

    private ResultActions postAsAdmin(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)
                .with(TestDataUtil.mockUser("ROLE_ADMIN")));
    }

    @Test
    void createSchoolWithEmptyObjectIs400WithNameMessage() throws Exception {
        postAsAdmin("/api/v1/schools", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void createSchoolWithBlankNameIs400() throws Exception {
        postAsAdmin("/api/v1/schools", "{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void createSchoolWithTooLongNameIs400() throws Exception {
        postAsAdmin("/api/v1/schools", "{\"name\":\"" + "x".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void createSchoolWithTooLongCodeIs400() throws Exception {
        postAsAdmin("/api/v1/schools", "{\"name\":\"A\",\"code\":\"123456\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").isNotEmpty());
    }

    @Test
    void createSchoolWithTooLongAddressFieldIs400() throws Exception {
        postAsAdmin("/api/v1/schools", "{\"name\":\"A\",\"address\":{\"city\":\"" + "c".repeat(256) + "\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['address.city']").isNotEmpty());
    }

    @Test
    void createSchoolWithValidBodyIs201() throws Exception {
        postAsAdmin("/api/v1/schools", "{\"name\":\"Valid School\",\"code\":\"VS1\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void putAndPatchWithEmptyObjectKeepExistingValues() throws Exception {
        var school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        String name = school.getName();

        mockMvc.perform(patch("/api/v1/schools/" + school.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
        mockMvc.perform(put("/api/v1/schools/" + school.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void patchSchoolWithTooLongNameIs400() throws Exception {
        var school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        mockMvc.perform(patch("/api/v1/schools/" + school.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(51) + "\"}").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void createProgramWithEmptyObjectIs400() throws Exception {
        postAsAdmin("/api/v1/programs", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void createProgramWithTooLongNameIs400() throws Exception {
        postAsAdmin("/api/v1/programs", "{\"name\":\"" + "x".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void createCourseWithEmptyObjectIs400() throws Exception {
        postAsAdmin("/api/v1/courses", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void createCourseWithTooLongCodeIs400() throws Exception {
        postAsAdmin("/api/v1/courses", "{\"name\":\"A\",\"code\":\"123456\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").isNotEmpty());
    }
}
