package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.support.ResetStateExtension;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ExtendWith(ResetStateExtension.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccreditationControllerIntegrationTests {

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
    void duplicateAccreditationCodeReturnsConflict() throws Exception {
        var existing = TestDataUtil.createAccreditationDto();
        accreditationService.save(accreditationMapper.toEntity(existing));
        var duplicate = TestDataUtil.createAccreditationDto();
        duplicate.setCode(existing.getCode());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/accreditations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate))
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());
    }

    @Test
    void accreditationWithoutNameReturnsFieldError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/accreditations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ACC42\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").isNotEmpty());
    }

    @Test
    void updatingAccreditationWithItsOwnCodeStillWorks() throws Exception {
        var existing = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        var updated = TestDataUtil.createAccreditationDto();
        updated.setCode(existing.getCode());
        updated.setName("Updated accreditation");

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/accreditations/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated))
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Updated accreditation"));
    }

    @Test
    void deletingAnAccreditationStillLinkedToAProgramReturnsConflictAndKeepsIt() throws Exception {
        var accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        String programBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Programme\",\"code\":\"P105X\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int programId = objectMapper.readTree(programBody).get("id").asInt();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs/" + programId
                        + "/accreditations/" + accreditation.getId() + "?startsAt=2020&endsAt=2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/accreditations/" + accreditation.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations/" + accreditation.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());
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

    private void saveAccreditation(String name, String code) {
        AccreditationDTO dto = TestDataUtil.createAccreditationDto();
        dto.setName(name);
        dto.setCode(code);
        accreditationService.save(accreditationMapper.toEntity(dto));
    }

    @Test
    void anonymousCanListAndSearchAccreditationsAndWritesAreRefused() throws Exception {
        saveAccreditation("Anon Accreditation", "ANA01");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations"))
                .andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations/name/Anon"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].name").value("Anon Accreditation"));

        var body = "{\"name\":\"Nope\",\"code\":\"NOPE1\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/accreditations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/accreditations")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/accreditations/1")
                        .with(TestDataUtil.mockUser("ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void nameSearchAndListWithoutSortPageByNameThenId() throws Exception {
        for (var entry : new String[][] {{"Srch Zulu", "SZ1"}, {"Srch Alpha", "SA1"}, {"Srch Alpha", "SA2"}, {"Srch Mike", "SM1"}}) {
            saveAccreditation(entry[0], entry[1]);
        }

        for (var path : new String[] {"/api/v1/accreditations/name/Srch", "/api/v1/accreditations"}) {
            mockMvc.perform(MockMvcRequestBuilders.get(path))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SA1"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SA2"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].code").value("SM1"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[3].code").value("SZ1"));
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations/name/Srch?size=2&page=1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SZ1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(4));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations/name/Srch?sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SZ1"));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations?sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SZ1"));
    }

    @Test
    void listsKeepTheDefaultPageSizeAndRejectAnUnknownSort() throws Exception {
        for (var path : new String[] {"/api/v1/accreditations", "/api/v1/accreditations/name/x"}) {
            mockMvc.perform(MockMvcRequestBuilders.get(path))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20));
            mockMvc.perform(MockMvcRequestBuilders.get(path + "?sort=doesNotExist,asc"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest());
        }
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
