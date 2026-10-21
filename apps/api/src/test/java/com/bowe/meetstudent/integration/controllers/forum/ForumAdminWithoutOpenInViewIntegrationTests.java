package com.bowe.meetstudent.integration.controllers.forum;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin forum endpoints build their DTOs while their transaction is open: they work with open-in-view off. */
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class ForumAdminWithoutOpenInViewIntegrationTests extends ForumTestSupport {

    private static final String ADMIN = "/api/v1/admin/forum";

    @Test
    void reportsAndModerationAnswerWithoutAnOpenSession() throws Exception {
        var admin = admin();
        var t = thread(student("Awa"), "title");
        var r = reply(t, student("Moussa"), "reply");
        var onThread = reportOf(t, student("Fatou"), "spam", Instant.now());
        var onReply = reportOf(r, student("Ali"), "spam", Instant.now());

        mvc.perform(get(ADMIN + "/reports").with(as(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(post(ADMIN + "/reports/" + onThread.getId() + "/handle").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HANDLED"))
                .andExpect(jsonPath("$.reporterName").value("Fatou")).andExpect(jsonPath("$.threadTitle").value("title"));
        mvc.perform(post(ADMIN + "/reports/" + onReply.getId() + "/dismiss").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetType").value("REPLY"));

        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/hide").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorName").value("Awa"));
        mvc.perform(post(ADMIN + "/threads/" + t.getId() + "/lock").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true));
        mvc.perform(post(ADMIN + "/replies/" + r.getId() + "/hide").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorName").value("Moussa"));
    }
}
