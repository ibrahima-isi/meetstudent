package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.support.ResetStateExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin queue of Expert accounts: list by status, approve, reject, and the immediate effect on rights. */
@SpringBootTest
@ExtendWith(ResetStateExtension.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminExpertsIntegrationTests {

    private static final String URL = "/api/v1/admin/experts";

    @Autowired MockMvc mvc;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProgramService programs;
    @Autowired ProgramMapper programMapper;

    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> roles.save(Role.builder().name(name).build()));
    }

    private UserEntity user(String roleName, String email, ExpertStatus status) {
        return userService.saveUser(UserEntity.builder().firstname("Awa").lastname("Diop").email(email)
                .qualification("Maths").password("password").role(role(roleName)).expertStatus(status).build(), encoder);
    }

    private void registeredAt(int id, String timestamp) {
        jdbc.update("update users set created_at = ? where id = ?", java.sql.Timestamp.valueOf(timestamp), id);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return TestDataUtil.mockUser(9999, "ROLE_ADMIN");
    }

    private static String reason(String value) {
        return "{\"reason\":" + value + "}";
    }

    @Test
    void anonymousIs401AndStudentsAndExpertsAre403OnEveryEndpoint() throws Exception {
        var expert = user("ROLE_EXPERT", "p@example.com", ExpertStatus.PENDING);
        var paths = new String[] {URL + "/" + expert.getId() + "/approve", URL + "/" + expert.getId() + "/reject"};
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (var p : paths) mvc.perform(post(p)).andExpect(status().isUnauthorized());
        for (var r : new String[] {"ROLE_STUDENT", "ROLE_EXPERT"}) {
            mvc.perform(get(URL).with(TestDataUtil.mockUser(r))).andExpect(status().isForbidden());
            for (var p : paths) mvc.perform(post(p).with(TestDataUtil.mockUser(r))).andExpect(status().isForbidden());
        }
        assertThat(users.findById(expert.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.PENDING);
    }

    @Test
    void listFiltersByStatusIsNewestFirstAndExposesOnlyTheAdminFields() throws Exception {
        var old = user("ROLE_EXPERT", "old@example.com", ExpertStatus.PENDING);
        var tieA = user("ROLE_EXPERT", "a@example.com", ExpertStatus.PENDING);
        var tieB = user("ROLE_EXPERT", "b@example.com", ExpertStatus.PENDING);
        var newest = user("ROLE_EXPERT", "new@example.com", ExpertStatus.PENDING);
        var approved = user("ROLE_EXPERT", "ok@example.com", ExpertStatus.APPROVED);
        user("ROLE_STUDENT", "s@example.com", null);
        registeredAt(old.getId(), "2026-01-01 10:00:00");
        registeredAt(tieA.getId(), "2026-02-01 10:00:00");
        registeredAt(tieB.getId(), "2026-02-01 10:00:00");
        registeredAt(newest.getId(), "2026-03-01 10:00:00");

        mvc.perform(get(URL + "?status=PENDING").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.content[1].id").value(tieB.getId()))
                .andExpect(jsonPath("$.content[2].id").value(tieA.getId()))
                .andExpect(jsonPath("$.content[3].id").value(old.getId()))
                .andExpect(jsonPath("$.content[0].firstname").value("Awa"))
                .andExpect(jsonPath("$.content[0].lastname").value("Diop"))
                .andExpect(jsonPath("$.content[0].email").value("new@example.com"))
                .andExpect(jsonPath("$.content[0].qualification").value("Maths"))
                .andExpect(jsonPath("$.content[0].registeredAt").exists())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].role").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordVersion").doesNotExist());
        mvc.perform(get(URL + "?status=APPROVED").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(approved.getId()));
        mvc.perform(get(URL + "?status=REJECTED").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(URL).with(admin())).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get(URL + "?status=NOPE").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void listIsPagedCappedAndTheSortParameterIsIgnored() throws Exception {
        for (int i = 0; i < 5; i++) {
            var e = user("ROLE_EXPERT", "e" + i + "@example.com", ExpertStatus.PENDING);
            registeredAt(e.getId(), "2026-03-0" + (i + 1) + " 10:00:00");
        }
        mvc.perform(get(URL + "?size=2&page=1").with(admin()))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].email").value("e2@example.com"))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get(URL + "?sort=email,asc").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("e4@example.com"));
        mvc.perform(get(URL + "?size=100000").with(admin()))
                .andExpect(jsonPath("$.size").value(lessThanOrEqualTo(100)));
    }

    @Test
    void approveSetsStatusClearsTheReasonAndStampsTheDecisionDate() throws Exception {
        var expert = user("ROLE_EXPERT", "r@example.com", ExpertStatus.REJECTED);
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(reason("\"incomplet\""))).andExpect(status().isOk());
        mvc.perform(post(URL + "/" + expert.getId() + "/approve").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.decisionDate").exists());
        var saved = users.findById(expert.getId()).orElseThrow();
        assertThat(saved.getExpertStatus()).isEqualTo(ExpertStatus.APPROVED);
        assertThat(saved.getExpertStatusReason()).isNull();
        assertThat(saved.getExpertDecisionDate()).isNotNull();
    }

    @Test
    void rejectStoresATrimmedReasonAndAnApprovedExpertCanBeRejected() throws Exception {
        var expert = user("ROLE_EXPERT", "a@example.com", ExpertStatus.APPROVED);
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("\"  Diplôme illisible \"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Diplôme illisible"));
        assertThat(users.findById(expert.getId()).orElseThrow().getExpertStatusReason()).isEqualTo("Diplôme illisible");
    }

    @Test
    void rejectWithoutBodyOrWithBlankOrNullReasonStoresNoReason() throws Exception {
        var expert = user("ROLE_EXPERT", "b@example.com", ExpertStatus.PENDING);
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").doesNotExist());
        for (var body : new String[] {reason("\"   \""), reason("null"), "{}"}) {
            mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.reason").doesNotExist());
        }
        assertThat(users.findById(expert.getId()).orElseThrow().getExpertStatusReason()).isNull();
    }

    @Test
    void invalidReasonsAre400AndChangeNothing() throws Exception {
        var expert = user("ROLE_EXPERT", "c@example.com", ExpertStatus.PENDING);
        var bad = new String[] {reason("\"" + "x".repeat(256) + "\""), reason("\"a\\u0007b\""), reason("\"a\\u0000b\""),
                reason("\"a\\u001bb\""), reason("\"a\\u0085b\""),
                reason("\"a\\u202eb\""), reason("\"a\\u200bb\""), reason("\"a\\ufeffb\""),
                reason("\"a\\u2028b\""), reason("\"a\\u2029b\""), reason("\"a\\u2066b\""),
                reason("\"a\\ud800b\"")};
        for (var body : bad) {
            mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").exists());
        }
        var untouched = users.findById(expert.getId()).orElseThrow();
        assertThat(untouched.getExpertStatus()).isEqualTo(ExpertStatus.PENDING);
        assertThat(untouched.getExpertStatusReason()).isNull();
        assertThat(untouched.getExpertDecisionDate()).isNull();
        // 255 characters, and tab/newline/carriage return, are fine
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("\"" + "x".repeat(255) + "\"")))
                .andExpect(status().isOk());
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("\"a\\tb\\r\\nc\"")))
                .andExpect(status().isOk());
        assertThat(users.findById(expert.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.REJECTED);
    }

    @Test
    void accentedTextAndEmojiAreAValidReason() throws Exception {
        var expert = user("ROLE_EXPERT", "emoji@example.com", ExpertStatus.PENDING);
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("\"Diplôme à vérifier \uD83D\uDE00 çà\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("Diplôme à vérifier \uD83D\uDE00 çà"));
    }

    @Test
    void malformedJsonIs400AndAnExpertStatusFieldIsIgnored() throws Exception {
        var expert = user("ROLE_EXPERT", "mal@example.com", ExpertStatus.PENDING);
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":"))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(expert.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.PENDING);
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expertStatus\":\"APPROVED\",\"reason\":\"non\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void aMalformedBodyOrExtraFieldsNeverReachTheStatus() throws Exception {
        var expert = user("ROLE_EXPERT", "d@example.com", ExpertStatus.PENDING);
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\",\"status\":\"APPROVED\",\"role\":{\"id\":1}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(users.findById(expert.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.REJECTED);
    }

    @Test
    void nonExpertTargetsAre409AndUnknownIdsAre404() throws Exception {
        var student = user("ROLE_STUDENT", "s@example.com", null);
        var adminUser = user("ROLE_ADMIN", "adm@example.com", null);
        for (var id : new int[] {student.getId(), adminUser.getId()}) {
            mvc.perform(post(URL + "/" + id + "/approve").with(admin())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
            mvc.perform(post(URL + "/" + id + "/reject").with(admin())).andExpect(status().isConflict());
        }
        assertThat(users.findById(student.getId()).orElseThrow().getExpertStatus()).isNull();
        mvc.perform(post(URL + "/987654/approve").with(admin())).andExpect(status().isNotFound());
        mvc.perform(post(URL + "/987654/reject").with(admin())).andExpect(status().isNotFound());
    }

    private String login(String email) throws Exception {
        var body = mvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("accessToken").asText();
    }

    @Test
    void decisionsApplyToTheSameRealTokenWithoutReconnecting() throws Exception {
        var expert = user("ROLE_EXPERT", "live@example.com", ExpertStatus.PENDING);
        var program = programs.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var body = "{\"note\":4.0,\"programId\":" + program.getId() + ",\"userId\":" + expert.getId() + "}";
        var bearer = "Bearer " + login("live@example.com");

        mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post(URL + "/" + expert.getId() + "/approve").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", bearer)).andExpect(status().isCreated());
        mvc.perform(post(URL + "/" + expert.getId() + "/reject").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    private void changeRole(int userId, String roleName) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/" + userId + "/role")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + role(roleName).getId() + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void aDemotedAdminLosesAccessWithTheOldToken() throws Exception {
        var other = user("ROLE_ADMIN", "demote@example.com", null);
        var bearer = "Bearer " + login("demote@example.com");
        mvc.perform(get(URL).header("Authorization", bearer)).andExpect(status().isOk());
        changeRole(other.getId(), "ROLE_ADMIN"); // same role: nothing is revoked
        mvc.perform(get(URL).header("Authorization", bearer)).andExpect(status().isOk());
        changeRole(other.getId(), "ROLE_STUDENT");
        mvc.perform(get(URL).header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void aDemotedExpertLosesTheirRatingRightsWithTheOldToken() throws Exception {
        var expert = user("ROLE_EXPERT", "demoted@example.com", ExpertStatus.APPROVED);
        var program = programs.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var body = "{\"note\":4.0,\"programId\":" + program.getId() + ",\"userId\":" + expert.getId() + "}";
        var bearer = "Bearer " + login("demoted@example.com");
        changeRole(expert.getId(), "ROLE_STUDENT");
        mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }
}
