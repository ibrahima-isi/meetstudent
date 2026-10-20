package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Approval of Expert (teacher) accounts: registration status and the server-side rating guard. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpertApprovalIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired PasswordEncoder encoder;
    @Autowired ProgramService programs;
    @Autowired ProgramMapper programMapper;
    @Autowired CourseService courses;
    @Autowired CourseMapper courseMapper;
    @Autowired SchoolService schools;
    @Autowired SchoolMapper schoolMapper;

    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> roles.save(Role.builder().name(name).build()));
    }

    private UserEntity user(String roleName, String email, ExpertStatus status) {
        return userService.saveUser(UserEntity.builder().firstname("T").lastname("U").email(email)
                .password("password").role(role(roleName)).expertStatus(status).build(), encoder);
    }

    private void setStatus(int userId, ExpertStatus status) {
        var u = users.findById(userId).orElseThrow();
        u.setExpertStatus(status);
        users.save(u);
    }

    private String register(String roleName, String email) {
        return "{\"firstname\":\"Awa\",\"lastname\":\"Diop\",\"email\":\"" + email
                + "\",\"password\":\"password109\",\"confirmedPassword\":\"password109\",\"role\":\"" + roleName
                + "\",\"qualification\":\"Maths\"}";
    }

    private void ratePathAs(String path, String body, int userId, int expected) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(TestDataUtil.mockUser(userId, "ROLE_EXPERT"))).andExpect(status().is(expected));
    }

    private String programBody(int userId) {
        var program = programs.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        return "{\"note\":4.0,\"programId\":" + program.getId() + ",\"userId\":" + userId + "}";
    }

    private String courseBody(int userId) {
        var course = courses.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        return "{\"note\":4.0,\"courseId\":" + course.getId() + ",\"userId\":" + userId + "}";
    }

    @Test
    void teacherRegistrationIsPendingAndStudentRegistrationHasNoStatus() throws Exception {
        role("ROLE_STUDENT");
        role("ROLE_EXPERT");
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(register("ROLE_EXPERT", "teacher109@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expertStatus").value("PENDING"));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(register("ROLE_STUDENT", "student109@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expertStatus").doesNotExist());
        assertThat(users.findByEmailIgnoreCase("teacher109@example.com").orElseThrow().getExpertStatus())
                .isEqualTo(ExpertStatus.PENDING);
        assertThat(users.findByEmailIgnoreCase("student109@example.com").orElseThrow().getExpertStatus()).isNull();
    }

    @Test
    void pendingAndRejectedExpertsCannotRateProgramsOrCourses() throws Exception {
        for (var status : new ExpertStatus[] {ExpertStatus.PENDING, ExpertStatus.REJECTED}) {
            var expert = user("ROLE_EXPERT", status.name().toLowerCase() + "@example.com", status);
            ratePathAs("/api/v1/program-rates", programBody(expert.getId()), expert.getId(), 403);
            ratePathAs("/api/v1/course-rates", courseBody(expert.getId()), expert.getId(), 403);
        }
    }

    @Test
    void approvedExpertCanRateProgramsAndCourses() throws Exception {
        var expert = user("ROLE_EXPERT", "approved@example.com", ExpertStatus.APPROVED);
        ratePathAs("/api/v1/program-rates", programBody(expert.getId()), expert.getId(), 201);
        ratePathAs("/api/v1/course-rates", courseBody(expert.getId()), expert.getId(), 201);
    }

    @Test
    void decisionAppliesToTheSameSessionWithoutReconnecting() throws Exception {
        var expert = user("ROLE_EXPERT", "later@example.com", ExpertStatus.PENDING);
        var body = programBody(expert.getId());
        ratePathAs("/api/v1/program-rates", body, expert.getId(), 403);
        setStatus(expert.getId(), ExpertStatus.APPROVED);
        ratePathAs("/api/v1/program-rates", body, expert.getId(), 201);
        setStatus(expert.getId(), ExpertStatus.REJECTED);
        ratePathAs("/api/v1/program-rates", body, expert.getId(), 403);
    }

    @Test
    void pendingExpertCanStillRateASchoolLikeAStudent() throws Exception {
        var expert = user("ROLE_EXPERT", "school@example.com", ExpertStatus.PENDING);
        var school = schools.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        ratePathAs("/api/v1/school-rates",
                "{\"note\":4.0,\"schoolId\":" + school.getId() + ",\"userId\":" + expert.getId() + "}", expert.getId(), 201);
    }

    @Test
    void studentStillCannotRateProgramsAndAnonymousIsRefused() throws Exception {
        var student = user("ROLE_STUDENT", "stud@example.com", null);
        mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                .content(programBody(student.getId())).with(TestDataUtil.mockUser(student.getId(), "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                .content(programBody(student.getId()))).andExpect(status().isUnauthorized());
    }

    @Test
    void statusIsReturnedToTheOwnerAndAnotherUserIsRefused() throws Exception {
        var expert = user("ROLE_EXPERT", "owner@example.com", ExpertStatus.REJECTED);
        var other = user("ROLE_EXPERT", "peer@example.com", ExpertStatus.APPROVED);
        mvc.perform(get("/api/v1/users/id/" + expert.getId()).with(TestDataUtil.mockUser(expert.getId(), "ROLE_EXPERT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expertStatus").value("REJECTED"));
        mvc.perform(get("/api/v1/users/id/" + expert.getId()).with(TestDataUtil.mockUser(other.getId(), "ROLE_EXPERT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminChangingARoleKeepsTheStatusCoherent() throws Exception {
        var student = user("ROLE_STUDENT", "promote@example.com", null);
        var expertRole = role("ROLE_EXPERT");
        mvc.perform(patch("/api/v1/users/" + student.getId() + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + expertRole.getId() + "}").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk());
        assertThat(users.findById(student.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.APPROVED);
        var studentRole = role("ROLE_STUDENT");
        mvc.perform(patch("/api/v1/users/" + student.getId() + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + studentRole.getId() + "}").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk());
        assertThat(users.findById(student.getId()).orElseThrow().getExpertStatus()).isNull();
    }

    private void adminSetsRole(int userId, Role target) throws Exception {
        mvc.perform(patch("/api/v1/users/" + userId + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + target.getId() + "}").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminSettingTheSameExpertRoleLeavesTheDecisionUntouched() throws Exception {
        var expert = user("ROLE_EXPERT", "same@example.com", ExpertStatus.REJECTED);
        adminSetsRole(expert.getId(), role("ROLE_EXPERT"));
        assertThat(users.findById(expert.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.REJECTED);
    }

    @Test
    void aRejectedExpertWhoLeavesAndComesBackThroughTheAdminIsApproved() throws Exception {
        var expert = user("ROLE_EXPERT", "again@example.com", ExpertStatus.REJECTED);
        adminSetsRole(expert.getId(), role("ROLE_STUDENT"));
        adminSetsRole(expert.getId(), role("ROLE_EXPERT"));
        var saved = users.findById(expert.getId()).orElseThrow();
        assertThat(saved.getExpertStatus()).isEqualTo(ExpertStatus.APPROVED);
        assertThat(saved.getExpertDecisionDate()).isNotNull();
    }

    @Test
    void registrationCannotChooseItsOwnApprovalStatus() throws Exception {
        role("ROLE_EXPERT");
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(register("ROLE_EXPERT", "sneaky@example.com").replace("}", ",\"expertStatus\":\"APPROVED\"}")))
                .andExpect(status().isCreated());
        assertThat(users.findByEmailIgnoreCase("sneaky@example.com").orElseThrow().getExpertStatus())
                .isEqualTo(ExpertStatus.PENDING);
    }
}
