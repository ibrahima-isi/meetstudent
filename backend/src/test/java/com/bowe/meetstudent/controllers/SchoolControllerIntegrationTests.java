package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.config.TestSecurityConfig;
import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.SchoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import(TestSecurityConfig.class)
class SchoolControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private SchoolMapper schoolMapper;

    @Test
    void testThatCreateSchoolReturnsHttpStatus201Created() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();

        String json = objectMapper.writeValueAsString(schoolDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.status().is(201)
        );
    }

    @Test
    void testThatSchoolCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();

        String json = objectMapper.writeValueAsString(schoolDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.status().is(201)
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(schoolDTO.getName())
        );
    }

    @Test
    void testThatGetAllSchoolsReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/schools")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatGetAllSchoolsReturnsAListOfSchools() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        SchoolDTO schoolDTO0 = TestDataUtil.createSchoolDto();
        SchoolDTO schoolDTO1 = TestDataUtil.createSchoolDto();

        this.schoolService.save(this.schoolMapper.toEntity(schoolDTO));
        this.schoolService.save(this.schoolMapper.toEntity(schoolDTO0));
        this.schoolService.save(this.schoolMapper.toEntity(schoolDTO1));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/schools")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].id").value(1)
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].name").value(schoolDTO.getName())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[1].id").isNumber()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[2].id").isNumber()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[2].name").value(schoolDTO1.getName())
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }
}
