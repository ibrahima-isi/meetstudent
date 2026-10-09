package com.bowe.meetstudent.integration.security;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessControlIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolMapper schoolMapper;
    @Autowired private ProgramService programService;
    @Autowired private ProgramMapper programMapper;
    @Autowired private CourseService courseService;
    @Autowired private CourseMapper courseMapper;

    // ---- PATCH is admin-only ------------------------------------------------------------

    @Test
    void studentCannotPatchSchool() throws Exception {
        var school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        mockMvc.perform(patch("/api/v1/schools/" + school.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HACKED\"}")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertNotEquals("HACKED",
                schoolService.getSchoolById(school.getId()).orElseThrow().getName());
    }

    @Test
    void adminCanPatchSchool() throws Exception {
        var school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        mockMvc.perform(patch("/api/v1/schools/" + school.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void studentCannotPatchProgram() throws Exception {
        var program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        mockMvc.perform(patch("/api/v1/programs/" + program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HACKED\"}")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanPatchProgram() throws Exception {
        var program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        mockMvc.perform(patch("/api/v1/programs/" + program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotPatchCourse() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        mockMvc.perform(patch("/api/v1/courses/" + course.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HACKED\"}")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanPatchCourse() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        mockMvc.perform(patch("/api/v1/courses/" + course.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    // ---- listing users is admin-only ---------------------------------------------------

    @Test
    void studentCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotListUsersByRole() throws Exception {
        mockMvc.perform(get("/api/v1/users/role/STUDENT").with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ---- 403 vs 401 and error body --------------------------------------------------------

    @Test
    void authenticatedUserHittingAdminUrlRuleGets403WithStandardBody() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/schools"));
    }

    @Test
    void missingTokenOnAdminUrlRuleGets401() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void invalidTokenOnAdminUrlRuleGets401() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .header("Authorization", "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
