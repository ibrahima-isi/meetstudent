package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ranking behind the web "Rankings" page: {@code GET /schools?sortRate=most} paged by ten.
 * Runs on H2 in the full suite and on migrated PostgreSQL with datasource overrides. Hibernate
 * drops {@code NULLS LAST} on H2, whose test URL sorts nulls high, so "unrated last" is only
 * asserted where it can hold (PostgreSQL); everything else is asserted on both.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchoolRankingPostgresIntegrationTests {
    private static final int PAGE_SIZE = 3;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoleService roles;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;
    @Autowired SchoolService schools;
    @Autowired SchoolMapper schoolMapper;
    @Autowired DataSource dataSource;

    @Test
    void rankingPagesByAverageDescendingThenNameThenIdWithUnratedAtTheEndAndAnExactTotal() throws Exception {
        var role = roles.findRoleByName("ROLE_STUDENT").orElseGet(() -> roles.createRole(Role.builder().name("ROLE_STUDENT").build()));
        var first = student(role);
        var second = student(role);
        var before = fetch(0, 1).get("totalElements").asInt();

        var unratedB = school("Rk Nope B");
        var zulu = school("Rk Zulu");
        var alphaOne = school("Rk Alpha");
        var alphaTwo = school("Rk Alpha");
        var mid = school("Rk Mid");
        var top = school("Rk Top");
        var low = school("Rk Low");
        var unratedA = school("Rk Nope A");
        rate(zulu, first, 4.0);
        rate(zulu, second, 4.0);
        rate(alphaOne, first, 4.0);
        rate(alphaTwo, first, 4.0);
        // 5 and 3 average to the same 4.0 as the others: the order comes from the mean, then name, then id.
        rate(mid, first, 5.0);
        rate(mid, second, 3.0);
        rate(top, first, 5.0);
        rate(low, first, 2.0);

        var expectedRated = List.of(top, alphaOne, alphaTwo, mid, zulu, low);
        var expectedUnrated = List.of(unratedA, unratedB);
        var ours = new ArrayList<>(expectedRated);
        ours.addAll(expectedUnrated);

        var total = before + ours.size();
        var walked = new ArrayList<Integer>();
        var averages = new ArrayList<Double>();
        var pages = 0;
        JsonNode page;
        do {
            page = fetch(pages, PAGE_SIZE);
            assertThat(page.get("totalElements").asInt()).as("totalElements on page %d", pages).isEqualTo(total);
            assertThat(page.get("number").asInt()).isEqualTo(pages);
            assertThat(page.get("size").asInt()).isEqualTo(PAGE_SIZE);
            var expectedOnPage = Math.min(PAGE_SIZE, total - pages * PAGE_SIZE);
            assertThat(page.get("content").size()).as("size of page %d", pages).isEqualTo(expectedOnPage);
            page.get("content").forEach(node -> {
                walked.add(node.get("id").asInt());
                averages.add(node.get("averageRate").isNull() ? null : node.get("averageRate").asDouble());
            });
            pages++;
        } while (!page.get("last").asBoolean());

        assertThat(pages).isEqualTo((total + PAGE_SIZE - 1) / PAGE_SIZE);
        assertThat(pages).as("at least pages 0, 1 and 2 exist").isGreaterThanOrEqualTo(3);
        assertThat(walked).as("no school repeated or skipped across pages").doesNotHaveDuplicates().hasSize(total);

        assertThat(walked.stream().filter(expectedRated::contains).toList()).isEqualTo(expectedRated);
        assertThat(walked.stream().filter(expectedUnrated::contains).toList()).isEqualTo(expectedUnrated);

        var ratedAverages = averages.stream().filter(average -> average != null).toList();
        assertThat(ratedAverages).as("averages never increase down the ranking").isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(ratedAverages).contains(4.0, 5.0, 2.0);
        if (onPostgres()) {
            var firstUnrated = averages.indexOf(null);
            assertThat(averages.subList(firstUnrated, averages.size())).as("nothing rated follows an unrated school").containsOnlyNulls();
        }
    }

    private boolean onPostgres() throws Exception {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        }
    }

    private JsonNode fetch(int page, int size) throws Exception {
        var body = mvc.perform(get("/api/v1/schools?sortRate=most&page=" + page + "&size=" + size))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private int student(Role role) {
        return users.saveUser(UserEntity.builder().firstname("Rank").lastname("Student")
                .email(UUID.randomUUID() + "@example.com").password("password").role(role).build(), encoder).getId();
    }

    /** Codes are unique and capped at 5 characters; a per-run stem keeps the test repeatable on a persistent database. */
    private final String runStem = "R" + UUID.randomUUID().toString().substring(0, 3);
    private int created;

    private int school(String name) {
        SchoolDTO dto = TestDataUtil.createSchoolDto();
        dto.setName(name);
        dto.setCode(runStem + (++created));
        return schools.save(schoolMapper.toEntity(dto)).getId();
    }

    private void rate(int schoolId, int userId, double note) throws Exception {
        mvc.perform(post("/api/v1/school-rates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": " + note + ", \"schoolId\": " + schoolId + ", \"userId\": " + userId + "}")
                        .with(TestDataUtil.mockUser(userId, "ROLE_STUDENT")))
                .andExpect(status().isCreated());
    }
}
