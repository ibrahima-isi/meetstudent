package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
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
                MockMvcRequestBuilders.post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
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
                MockMvcRequestBuilders.post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
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
                MockMvcRequestBuilders.get("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
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
                MockMvcRequestBuilders.get("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
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

    @Test
    void testThatGetSchoolByIdReturnsHttpStatus200AndSchool() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        com.bowe.meetstudent.entities.School savedSchool = this.schoolService.save(this.schoolMapper.toEntity(schoolDTO));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/" + savedSchool.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.id").value(savedSchool.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(schoolDTO.getName())
        );
    }

    @Test
    void testThatGetSchoolByIdReturns404WhenNotFound() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNotFound()
        );
    }

    @Test
    void testThatGetSchoolByNameReturnsResults() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        schoolDTO.setName("Specific Test University");
        this.schoolService.save(this.schoolMapper.toEntity(schoolDTO));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/name/Specific")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].name").value(schoolDTO.getName())
        );
    }

    @Test
    void testThatGetSchoolByCityReturnsResults() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        schoolDTO.getAddress().setCity("UniqueCityName");
        this.schoolService.save(this.schoolMapper.toEntity(schoolDTO));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/address/UniqueCityName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].address.city").value("UniqueCityName")
        );
    }

    @Test
    void testThatUpdateSchoolReturnsHttpStatus200AndUpdatedSchool() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        com.bowe.meetstudent.entities.School savedSchool = this.schoolService.save(this.schoolMapper.toEntity(schoolDTO));

        SchoolDTO updatedDto = TestDataUtil.createSchoolDto();
        updatedDto.setName("UPDATED NAME");

        String json = objectMapper.writeValueAsString(updatedDto);
        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/v1/schools/" + savedSchool.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value("UPDATED NAME")
        );
    }

    @Test
    void testThatPatchSchoolReturnsHttpStatus200AndPatchedSchool() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        com.bowe.meetstudent.entities.School savedSchool = this.schoolService.save(this.schoolMapper.toEntity(schoolDTO));

        SchoolDTO patchDto = new SchoolDTO();
        patchDto.setName("PATCHED NAME");

        String json = objectMapper.writeValueAsString(patchDto);
        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/schools/" + savedSchool.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value("PATCHED NAME")
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.code").value(schoolDTO.getCode())
        );
    }

    @Test
    void testThatDeleteSchoolReturnsHttpStatus200() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        com.bowe.meetstudent.entities.School savedSchool = this.schoolService.save(this.schoolMapper.toEntity(schoolDTO));

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/schools/" + savedSchool.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatDeleteSchoolReturns404WhenNotFound() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/schools/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNotFound()
        );
    }

    @Test
    void testThatSchoolCanBeCreatedWithNestedProgramsAndCourses() throws Exception {
        String json = """
            {
              "name": "MIT",
              "code": "MIT01",
              "programs": [
                {
                  "name": "Computer Science",
                  "code": "CS01",
                  "duration": 4,
                  "courses": [
                    {
                      "name": "Algorithms",
                      "code": "ALG01"
                    }
                  ]
                }
              ]
            }
            """;

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value("MIT")
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.programs[0].name").value("Computer Science")
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.programs[0].courses[0].name").value("Algorithms")
        );
    }

    @Test
    void testThatGetSchoolsSortedByRateReturnsCorrectOrder() throws Exception {
        SchoolDTO s1 = TestDataUtil.createSchoolDto();
        s1.setName("Best School");
        s1.setCode("BS001");
        com.bowe.meetstudent.entities.School school1 = schoolService.save(schoolMapper.toEntity(s1));

        SchoolDTO s2 = TestDataUtil.createSchoolDto();
        s2.setName("Average School");
        s2.setCode("AS001");
        com.bowe.meetstudent.entities.School school2 = schoolService.save(schoolMapper.toEntity(s2));
        
        // Rate school1 with 5.0, school2 with 3.0
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
    void testThatSearchSchoolsByProgramReturnsCorrectResults() throws Exception {
        SchoolDTO s = TestDataUtil.createSchoolDto();
        s.setName("Tech Institute");
        com.bowe.meetstudent.entities.School school = schoolService.save(schoolMapper.toEntity(s));
        
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Computer Science\", \"code\": \"CS99\", \"schoolId\": " + school.getId() + "}")
                .with(TestDataUtil.mockUser("ROLE_ADMIN")));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/search?program=Computer")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].name").value("Tech Institute")
        );
    }
}
