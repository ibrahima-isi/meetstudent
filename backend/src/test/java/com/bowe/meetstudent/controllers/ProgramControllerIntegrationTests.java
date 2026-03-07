package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.ProgramDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.SchoolService;
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
class ProgramControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProgramService programService;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private ProgramMapper programMapper;

    @Test
    void testThatCreateProgramReturnsHttpStatus201Created() throws Exception {
        ProgramDTO programDTO = TestDataUtil.createProgramDto();

        String json = objectMapper.writeValueAsString(programDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatProgramCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        ProgramDTO programDTO = TestDataUtil.createProgramDto();

        String json = objectMapper.writeValueAsString(programDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(programDTO.getName())
        );
    }

    @Test
    void testThatCreateProgramWithSchoolReturnsHttpStatus201() throws Exception {
        School school = new School();
        school.setName("Test School");
        school.setCode("TSCH1");
        school = schoolService.save(school);

        ProgramDTO programDTO = TestDataUtil.createProgramDto();
        programDTO.setSchoolId(school.getId());

        String json = objectMapper.writeValueAsString(programDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(programDTO.getName())
        );
    }

    @Test
    void testThatGetAllProgramsReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatGetAllProgramsReturnsAListOfPrograms() throws Exception {
        ProgramDTO programDTO0 = TestDataUtil.createProgramDto();
        ProgramDTO programDTO1 = TestDataUtil.createProgramDto();
        programDTO1.setCode("PRG01"); // avoid unique constraint violation if faker duplicates

        this.programService.save(this.programMapper.toEntity(programDTO0));
        this.programService.save(this.programMapper.toEntity(programDTO1));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/programs")
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
