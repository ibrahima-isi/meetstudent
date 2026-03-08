package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.AccreditationDTO;
import com.bowe.meetstudent.mappers.implementations.AccreditationMapper;
import com.bowe.meetstudent.services.AccreditationService;
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
class AccreditationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccreditationService accreditationService;

    @Autowired
    private AccreditationMapper accreditationMapper;

    @Test
    void testThatCreateAccreditationReturnsHttpStatus201Created() throws Exception {
        AccreditationDTO accreditationDTO = TestDataUtil.createAccreditationDto();

        String json = objectMapper.writeValueAsString(accreditationDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/accreditations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatAccreditationCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        AccreditationDTO accreditationDTO = TestDataUtil.createAccreditationDto();

        String json = objectMapper.writeValueAsString(accreditationDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/accreditations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(accreditationDTO.getName())
        );
    }

    @Test
    void testThatGetAllAccreditationsReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/accreditations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatGetAllAccreditationsReturnsAListOfAccreditations() throws Exception {
        AccreditationDTO dto0 = TestDataUtil.createAccreditationDto();
        AccreditationDTO dto1 = TestDataUtil.createAccreditationDto();
        dto1.setCode("ACC01");

        this.accreditationService.save(this.accreditationMapper.toEntity(dto0));
        this.accreditationService.save(this.accreditationMapper.toEntity(dto1));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/accreditations")
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
