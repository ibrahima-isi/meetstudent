package com.bowe.meetstudent.integration.controllers.forum;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 5 messages per minute and per user, threads and replies counted together. */
class ForumRateLimitIntegrationTests extends ForumTestSupport {

    @org.junit.jupiter.api.BeforeEach
    void clean() {
        replies.deleteAll();
        threads.deleteAll();
    }

    @Test
    void theSixthMessageInTheWindowIs429WithRetryAfterThreadsAndRepliesCountingTogether() throws Exception {
        var user = student();
        var target = thread(student(), "target");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("t" + i, "b", null)).with(as(user))).andExpect(status().isCreated());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/forum/threads/" + target.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                    .content(replyBody("r" + i)).with(as(user))).andExpect(status().isCreated());
        }
        var refused = mvc.perform(post("/api/v1/forum/threads/" + target.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("too many")).with(as(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andReturn().getResponse();
        var retryAfter = Long.parseLong(refused.getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 60L);
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("too many", "b", null)).with(as(user))).andExpect(status().isTooManyRequests());

        assertThat(threads.count()).isEqualTo(4); // target + 3
        assertThat(replies.count()).isEqualTo(2);
    }

    @Test
    void theLimitIsPerUser() throws Exception {
        var busy = student();
        var calm = approvedExpert();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("t" + i, "b", null)).with(as(busy))).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("x", "b", null)).with(as(busy))).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("x", "b", null)).with(as(calm))).andExpect(status().isCreated());
    }

    @Test
    void invalidRequestsAndDeletesDoNotConsumeTheBudget() throws Exception {
        var user = student();
        var own = thread(user, "own");
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("", "b", null)).with(as(user))).andExpect(status().isBadRequest());
            mvc.perform(put("/api/v1/forum/threads/" + own.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("", "b", null)).with(as(user))).andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 5; i++) {
            var spare = thread(user, "spare" + i);
            mvc.perform(delete("/api/v1/forum/threads/" + spare.getId()).with(as(user))).andExpect(status().isNoContent());
        }
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("t" + i, "b", null)).with(as(user))).andExpect(status().isCreated());
        }
        // Deleting is still possible once the budget is spent.
        var last = thread(user, "last");
        mvc.perform(delete("/api/v1/forum/threads/" + last.getId()).with(as(user))).andExpect(status().isNoContent());
    }

    @Test
    void editsShareTheSameBudgetAsCreations() throws Exception {
        var user = student();
        var own = thread(user, "own");
        var ownReply = reply(own, user, "reply");
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("t", "b", null)).with(as(user))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/forum/threads/" + own.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("r")).with(as(user))).andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            mvc.perform(put("/api/v1/forum/threads/" + own.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("edit" + i, "b", null)).with(as(user))).andExpect(status().isOk());
        }
        var refused = mvc.perform(put("/api/v1/forum/replies/" + ownReply.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("edited")).with(as(user)))
                .andExpect(status().isTooManyRequests()).andReturn().getResponse();
        assertThat(Long.parseLong(refused.getHeader("Retry-After"))).isBetween(1L, 60L);
        mvc.perform(put("/api/v1/forum/threads/" + own.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("edit", "b", null)).with(as(user))).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("t", "b", null)).with(as(user))).andExpect(status().isTooManyRequests());
        assertThat(replies.findById(ownReply.getId()).orElseThrow().getBody()).isEqualTo("reply");
    }

    @Test
    void aForbiddenWriterStaysForbiddenAndWritesNothing() throws Exception {
        var pending = pendingExpert();
        for (int i = 0; i < 7; i++) {
            mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("t", "b", null)).with(as(pending))).andExpect(status().isForbidden());
        }
        assertThat(threads.count()).isZero();
    }
}
