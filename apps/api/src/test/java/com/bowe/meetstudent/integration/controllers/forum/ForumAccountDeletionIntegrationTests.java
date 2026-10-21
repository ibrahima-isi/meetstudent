package com.bowe.meetstudent.integration.controllers.forum;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deleting an account keeps the messages (author becomes null); deleting a school only detaches its threads. */
class ForumAccountDeletionIntegrationTests extends ForumTestSupport {

    @Test
    void deletingTheAccountKeepsThreadsAndRepliesAnonymised() throws Exception {
        var leaver = student("Leaving");
        var stayer = student("Staying");
        var t = thread(leaver, "left behind");
        var mixed = thread(stayer, "other thread");
        var ownReply = reply(mixed, leaver, "reply by leaver");
        var strangerReply = reply(t, stayer, "reply by stayer");

        mvc.perform(delete("/api/v1/users/" + leaver.getId()).with(as(leaver)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(threads.findById(t.getId())).isPresent();
        assertThat(threads.findWithAuthorById(t.getId()).orElseThrow().getAuthor()).isNull();
        assertThat(replies.findWithAuthorById(ownReply.getId()).orElseThrow().getAuthor()).isNull();
        assertThat(replies.findWithAuthorById(strangerReply.getId()).orElseThrow().getAuthor()).isNotNull();

        mvc.perform(get("/api/v1/forum/threads/" + t.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorDeleted").value(true))
                .andExpect(jsonPath("$.authorName").doesNotExist());
        mvc.perform(get("/api/v1/forum/threads/" + mixed.getId() + "/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].authorDeleted").value(true))
                .andExpect(jsonPath("$.content[0].body").value("reply by leaver"));
    }

    @Test
    void anAdminDeletingAUserLeavesTheirMessagesToo() throws Exception {
        var victim = approvedExpert();
        var t = thread(victim, "expert thread");
        mvc.perform(delete("/api/v1/users/" + victim.getId()).with(as(admin()))).andExpect(status().isNoContent());
        assertThat(threads.findWithAuthorById(t.getId()).orElseThrow().getAuthor()).isNull();
    }

    @Test
    void deletingASchoolKeepsItsThreadsWithoutSchool() throws Exception {
        var s = school();
        var t = thread(student(), s, "about a school", java.time.Instant.now());
        mvc.perform(delete("/api/v1/schools/" + s.getId()).with(as(admin()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/forum/threads/" + t.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolId").doesNotExist());
    }
}
