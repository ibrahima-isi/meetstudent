package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.Tag;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.mappers.implementations.TagMapper;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.TagService;
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

import java.util.List;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchoolControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private TagService tagService;

    @Autowired
    private SchoolMapper schoolMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testThatCreateSchoolReturnsHttpStatus201Created() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        String schoolJson = objectMapper.writeValueAsString(schoolDTO);

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schoolJson)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatSchoolCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        School savedSchool = schoolService.save(schoolMapper.toEntity(schoolDTO));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/" + savedSchool.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.id").value(savedSchool.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(schoolDTO.getName())
        );
    }

    @Test
    void testThatGetAllSchoolsReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatGetSchoolsSortedByRateReturnsCorrectOrder() throws Exception {
        SchoolDTO s1 = TestDataUtil.createSchoolDto();
        s1.setName("Best School");
        s1.setCode("BS001");
        School school1 = schoolService.save(schoolMapper.toEntity(s1));

        SchoolDTO s2 = TestDataUtil.createSchoolDto();
        s2.setName("Average School");
        s2.setCode("AS001");
        School school2 = schoolService.save(schoolMapper.toEntity(s2));
        
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/school-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": 5.0, \"schoolId\": " + school1.getId() + ", \"userId\": 1}")
                .with(TestDataUtil.mockUser("ROLE_STUDENT")));
        
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/school-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": 3.0, \"schoolId\": " + school2.getId() + ", \"userId\": 1}")
                .with(TestDataUtil.mockUser("ROLE_STUDENT")));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools?sortRate=most")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].id").value(school1.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[1].id").value(school2.getId())
        );
    }

    @Test
    void testThatSearchSchoolsByTagReturnsCorrectResults() throws Exception {
        Tag publicTag = tagService.findOrCreate("PUBLIC");
        Tag privateTag = tagService.findOrCreate("PRIVATE");

        School school1 = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        school1.setName("Public School");
        school1.setCode("PUB01");
        school1.setTags(List.of(publicTag));
        schoolService.save(school1);

        School school2 = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        school2.setName("Private School");
        school2.setCode("PRV01");
        school2.setTags(List.of(privateTag));
        schoolService.save(school2);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/search?tag=PUBLIC")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].name").value("Public School")
        );
    }
}
