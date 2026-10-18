package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMessagesIntegrationTests {

    private static final String URL = "/api/v1/admin/messages";

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    private ContactMessage save(String subject, Instant createdAt) {
        return repository.save(ContactMessage.builder()
                .senderEmail("awa@example.com").subject(subject).body("Ligne 1\nLigne 2 <b>x</b>")
                .language("fr").createdAt(createdAt).build());
    }

    @Test
    void anonymousIs401AndNonAdminsAre403OnBothEndpoints() throws Exception {
        var saved = save("S", Instant.parse("2026-01-01T00:00:00Z"));
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL + "/" + saved.getId())).andExpect(status().isUnauthorized());
        for (var role : new String[] {"ROLE_STUDENT", "ROLE_EXPERT"}) {
            mockMvc.perform(get(URL).with(TestDataUtil.mockUser(role))).andExpect(status().isForbidden());
            mockMvc.perform(delete(URL + "/" + saved.getId()).with(TestDataUtil.mockUser(role)))
                    .andExpect(status().isForbidden());
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void listIsNewestFirstWithIdTieBreakAndExposesOnlyThePublicFields() throws Exception {
        var t = Instant.parse("2026-03-01T10:00:00Z");
        save("old", t.minusSeconds(60));
        var tieA = save("tieA", t);
        var tieB = save("tieB", t);
        save("newest", t.plusSeconds(60));

        mockMvc.perform(get(URL).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].subject").value("newest"))
                .andExpect(jsonPath("$.content[1].id").value(tieB.getId()))
                .andExpect(jsonPath("$.content[2].id").value(tieA.getId()))
                .andExpect(jsonPath("$.content[3].subject").value("old"))
                .andExpect(jsonPath("$.content[0].email").value("awa@example.com"))
                .andExpect(jsonPath("$.content[0].message").value("Ligne 1\nLigne 2 <b>x</b>"))
                .andExpect(jsonPath("$.content[0].language").value("fr"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].senderEmail").doesNotExist())
                .andExpect(jsonPath("$.content[0].body").doesNotExist());
    }

    @Test
    void listIsPagedAndTheSortParameterCannotReorderIt() throws Exception {
        var t = Instant.parse("2026-03-01T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            save("m" + i, t.plusSeconds(i));
        }
        mockMvc.perform(get(URL + "?size=2&page=1").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].subject").value("m2"))
                .andExpect(jsonPath("$.content[1].subject").value("m1"))
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(get(URL + "?sort=subject,asc").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value("m4"));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        save("a", Instant.parse("2026-03-01T10:00:00Z"));
        mockMvc.perform(get(URL).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(jsonPath("$.size").value(20));
        mockMvc.perform(get(URL + "?size=100000").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(lessThanOrEqualTo(100)));
    }

    @Test
    void deleteRemovesTheMessageAnd404sOnUnknownIds() throws Exception {
        var saved = save("bye", Instant.parse("2026-03-01T10:00:00Z"));
        mockMvc.perform(delete(URL + "/" + saved.getId()).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(saved.getId())).isEmpty();

        mockMvc.perform(delete(URL + "/" + saved.getId()).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void listExposesTheMailDeliveryState() throws Exception {
        var sentAt = Instant.parse("2026-03-01T10:00:05Z");
        var saved = save("delivered", Instant.parse("2026-03-01T10:00:00Z"));
        saved.setMailStatus(com.bowe.meetstudent.entities.enums.ContactMailStatus.SENT);
        saved.setMailAttempts(2);
        saved.setMailSentAt(sentAt);
        repository.save(saved);
        save("fresh", Instant.parse("2026-02-01T10:00:00Z"));

        mockMvc.perform(get(URL).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].mailStatus").value("SENT"))
                .andExpect(jsonPath("$.content[0].mailAttempts").value(2))
                .andExpect(jsonPath("$.content[0].mailSentAt").value("2026-03-01T10:00:05Z"))
                .andExpect(jsonPath("$.content[1].mailStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[1].mailAttempts").value(0))
                .andExpect(jsonPath("$.content[1].mailSentAt").doesNotExist());
    }
}
