package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.ProgramDTO;
import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.AccreditationMapper;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.services.AccreditationService;
import com.bowe.meetstudent.services.ProgramAccreditationService;
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
class ProgramControllerIT {

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

    @Autowired
    private ProgramAccreditationService programAccreditationService;

    @Autowired
    private AccreditationService accreditationService;

    @Autowired
    private AccreditationMapper accreditationMapper;

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

    @Test
    void testThatLinkAccreditationReturnsHttpStatus201() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs/" + program.getId() + "/accreditations/" + acc.getId())
                        .param("startsAt", "2020")
                        .param("endsAt", "2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.startsAt").value(2020)
        );
    }

    @Test
    void testThatGetProgramAccreditationsReturnsList() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        
        programAccreditationService.addAccreditationToProgram(program.getId(), acc.getId(), 2020, 2025);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId() + "/accreditations")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$[0].startsAt").value(2020)
        );
    }

    @Test
    void testThatUnlinkAccreditationReturnsHttpStatus204() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        
        programAccreditationService.addAccreditationToProgram(program.getId(), acc.getId(), 2020, 2025);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/programs/" + program.getId() + "/accreditations/" + acc.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );
    }
}
