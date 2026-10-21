package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.entities.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Writing: who may post, validation, plain text, locking, ownership of edits and deletes. */
class ForumWriteIntegrationTests extends ForumTestSupport {

    @BeforeEach
    void clean() {
        replies.deleteAll();
        threads.deleteAll();
    }

    private void postThread(UserEntity user, int expected) throws Exception {
        var request = post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON).content(threadBody("Title", "Body", null));
        if (user != null) request.with(as(user));
        mvc.perform(request).andExpect(status().is(expected));
    }

    private void postReply(UserEntity user, int threadId, int expected) throws Exception {
        var request = post("/api/v1/forum/threads/" + threadId + "/replies").contentType(MediaType.APPLICATION_JSON).content(replyBody("A reply"));
        if (user != null) request.with(as(user));
        mvc.perform(request).andExpect(status().is(expected));
    }

    // ---- roles ---------------------------------------------------------------------------------

    @Test
    void onlyStudentsAndApprovedExpertsCreateThreads() throws Exception {
        postThread(null, 401);
        postThread(pendingExpert(), 403);
        postThread(rejectedExpert(), 403);
        postThread(admin(), 403);
        postThread(student(), 201);
        postThread(approvedExpert(), 201);
        assertThat(threads.count()).isEqualTo(2);
    }

    @Test
    void onlyStudentsAndApprovedExpertsReply() throws Exception {
        var t = thread(student(), "open");
        postReply(null, t.getId(), 401);
        postReply(pendingExpert(), t.getId(), 403);
        postReply(rejectedExpert(), t.getId(), 403);
        postReply(admin(), t.getId(), 403);
        postReply(student(), t.getId(), 201);
        postReply(approvedExpert(), t.getId(), 201);
        assertThat(replies.count()).isEqualTo(2);
    }

    @Test
    void anExpertRejectedAfterPostingCanNoLongerWriteWithTheSameToken() throws Exception {
        var expert = approvedExpert();
        postThread(expert, 201);
        var row = userRepository.findById(expert.getId()).orElseThrow();
        row.setExpertStatus(com.bowe.meetstudent.entities.enums.ExpertStatus.REJECTED);
        userRepository.save(row); // the status is read live: no new sign-in involved
        postThread(expert, 403);
    }

    // ---- creation ------------------------------------------------------------------------------

    @Test
    void createdThreadCarriesTheAuthorSchoolAndDefaults() throws Exception {
        var author = student("Awa");
        var s = school();
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("  Which master?  ", "Hello", s.getId())).with(as(author)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Which master?"))
                .andExpect(jsonPath("$.body").value("Hello"))
                .andExpect(jsonPath("$.schoolId").value(s.getId()))
                .andExpect(jsonPath("$.authorName").value("Awa"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.replyCount").value(0));
        var stored = threads.findAll().get(0);
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.isHidden()).isFalse();
    }

    @Test
    void aClientCannotChooseLockedHiddenAuthorOrDate() throws Exception {
        var author = student();
        var other = student();
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"body\":\"b\",\"locked\":true,\"hidden\":true,\"authorId\":" + other.getId()
                                + ",\"createdAt\":\"2001-01-01T00:00:00Z\"}").with(as(author)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.hidden").value(false));
        var stored = threads.findAll().get(0);
        assertThat(stored.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(stored.getCreatedAt().getEpochSecond()).isGreaterThan(java.time.Instant.parse("2020-01-01T00:00:00Z").getEpochSecond());
    }

    @Test
    void anUnknownSchoolIsA400OnTheSchoolIdField() throws Exception {
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("t", "b", 999999)).with(as(student())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.schoolId").exists());
        assertThat(threads.count()).isZero();
    }

    @Test
    void replyToAnUnknownOrHiddenThreadIs404() throws Exception {
        var user = student();
        postReply(user, 999999, 404);
        var t = thread(user, "gone");
        t.setHidden(true);
        threads.save(t);
        postReply(user, t.getId(), 404);
    }

    // ---- validation ----------------------------------------------------------------------------

    @Test
    void titleAndBodyAreBounded() throws Exception {
        var user = student();
        var ok = post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("t".repeat(150), "b".repeat(5000), null)).with(as(user));
        mvc.perform(ok).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("t".repeat(151), "b", null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").exists());
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("t", "b".repeat(5001), null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("   ", "b", null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").exists());
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}").with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());

        var t = thread(user, "for replies");
        mvc.perform(post("/api/v1/forum/threads/" + t.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("b".repeat(5001))).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());
        mvc.perform(post("/api/v1/forum/threads/" + t.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody(" \n ")).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());
        assertThat(threads.count()).isEqualTo(2);
        assertThat(replies.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"‮", "​", "﻿", " ", "\u0000", "\u0007", "⁦"})
    void invisibleAndControlCharactersAreRefused(String forbidden) throws Exception {
        var user = student();
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("ti" + forbidden + "tle", "body", null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").exists());
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("title", "bo" + forbidden + "dy", null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());
        var t = thread(user, "x");
        mvc.perform(post("/api/v1/forum/threads/" + t.getId() + "/replies").contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("re" + forbidden + "ply")).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());
        mvc.perform(put("/api/v1/forum/threads/" + t.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("ti" + forbidden + "tle", "body", null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").exists());
        var r = reply(t, user, "own reply");
        mvc.perform(put("/api/v1/forum/replies/" + r.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("re" + forbidden + "ply")).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());
        assertThat(replies.count()).isEqualTo(1);
    }

    @Test
    void aTextMadeOnlyOfInvisibleFillersIsRefused() throws Exception {
        var user = student();
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("\u3164\u3164", "body", null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").exists());
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("title", "\u2800\u2800\u2800", null)).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body").exists());
        assertThat(threads.count()).isZero();
    }

    @Test
    void tabsAndLineBreaksAreKept() throws Exception {
        mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("t", "line1\nline2\r\n\tindented", null)).with(as(student())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("line1\nline2\r\n\tindented"));
    }

    // ---- plain text ----------------------------------------------------------------------------

    @Test
    void scriptPayloadsAreStoredAsIsAndReturnedAsText() throws Exception {
        var user = student();
        var payloadTitle = "<script>alert(1)</script>";
        var payloadBody = "<img src=x onerror=alert(1)> **bold** [x](javascript:alert(1)) &amp;";
        var created = mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody(payloadTitle, payloadBody, null)).with(as(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(payloadTitle))
                .andExpect(jsonPath("$.body").value(payloadBody))
                .andReturn().getResponse().getContentAsString();
        var id = json.readTree(created).get("id").asInt();

        assertThat(threads.findById(id).orElseThrow().getTitle()).isEqualTo(payloadTitle);
        assertThat(threads.findById(id).orElseThrow().getBody()).isEqualTo(payloadBody);

        mvc.perform(post("/api/v1/forum/threads/" + id + "/replies").contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody(payloadTitle)).with(as(user)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.body").value(payloadTitle));

        var response = mvc.perform(get("/api/v1/forum/threads/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(payloadTitle)).andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    // ---- lock ----------------------------------------------------------------------------------

    @Test
    void aLockedThreadRefusesRepliesWith409ButStaysReadable() throws Exception {
        var user = student();
        var t = thread(user, "locked");
        t.setLocked(true);
        threads.save(t);
        reply(t, user, "before the lock");

        postReply(user, t.getId(), 409);
        postReply(approvedExpert(), t.getId(), 409);
        assertThat(replies.count()).isEqualTo(1);
        mvc.perform(get("/api/v1/forum/threads/" + t.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true));
        mvc.perform(get("/api/v1/forum/threads/" + t.getId() + "/replies")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void aLockedThreadStillLetsAuthorsEditAndDeleteTheirOwnMessages() throws Exception {
        var user = student();
        var t = thread(user, "locked");
        var r = reply(t, user, "mine");
        t.setLocked(true);
        threads.save(t);

        mvc.perform(put("/api/v1/forum/replies/" + r.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("edited")).with(as(user))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/forum/replies/" + r.getId()).with(as(user))).andExpect(status().isNoContent());
    }

    // ---- edit / delete -------------------------------------------------------------------------

    @Test
    void theAuthorEditsAThreadAndTheDateOfEditAppears() throws Exception {
        var user = student();
        var s = school();
        var t = thread(user, s, "before", java.time.Instant.now());
        mvc.perform(put("/api/v1/forum/threads/" + t.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(threadBody("after", "new body", null)).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("after"))
                .andExpect(jsonPath("$.body").value("new body"))
                .andExpect(jsonPath("$.schoolId").value(s.getId()))
                .andExpect(jsonPath("$.modifiedAt").exists());
        assertThat(threads.findById(t.getId()).orElseThrow().getTitle()).isEqualTo("after");
    }

    @Test
    void nobodyButTheAuthorEditsOrDeletesAThread() throws Exception {
        var author = student();
        var t = thread(author, "mine");
        var other = student();
        for (var intruder : new UserEntity[] {other, approvedExpert(), admin()}) {
            mvc.perform(put("/api/v1/forum/threads/" + t.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(threadBody("hijacked", "hijacked", null)).with(as(intruder)))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/forum/threads/" + t.getId()).with(as(intruder))).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/v1/forum/threads/" + t.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("x", "x", null))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/forum/threads/" + t.getId())).andExpect(status().isUnauthorized());
        assertThat(threads.findById(t.getId()).orElseThrow().getTitle()).isEqualTo("mine");
    }

    @Test
    void theAuthorDeletesAThreadAndItsRepliesGoWithIt() throws Exception {
        var author = student();
        var t = thread(author, "to delete");
        reply(t, student(), "someone else's reply");
        mvc.perform(delete("/api/v1/forum/threads/" + t.getId()).with(as(author))).andExpect(status().isNoContent());
        assertThat(threads.findById(t.getId())).isEmpty();
        assertThat(replies.count()).isZero();
        mvc.perform(delete("/api/v1/forum/threads/" + t.getId()).with(as(author))).andExpect(status().isNotFound());
    }

    @Test
    void theAuthorEditsAndDeletesAReplyButNobodyElseDoes() throws Exception {
        var author = student();
        var t = thread(author, "thread");
        var r = reply(t, author, "original");
        for (var intruder : new UserEntity[] {student(), approvedExpert(), admin()}) {
            mvc.perform(put("/api/v1/forum/replies/" + r.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(replyBody("hijacked")).with(as(intruder))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/forum/replies/" + r.getId()).with(as(intruder))).andExpect(status().isForbidden());
        }
        assertThat(replies.findById(r.getId()).orElseThrow().getBody()).isEqualTo("original");

        mvc.perform(put("/api/v1/forum/replies/" + r.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("fixed")).with(as(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("fixed"))
                .andExpect(jsonPath("$.modifiedAt").exists());
        mvc.perform(delete("/api/v1/forum/replies/" + r.getId()).with(as(author))).andExpect(status().isNoContent());
        assertThat(replies.count()).isZero();
        mvc.perform(delete("/api/v1/forum/replies/" + r.getId()).with(as(author))).andExpect(status().isNotFound());
    }

    @Test
    void aPendingOrRejectedExpertCannotEditOrDeleteEvenTheirOwnMessage() throws Exception {
        var expert = approvedExpert();
        var t = thread(expert, "by expert");
        var r = reply(t, expert, "by expert");
        var row = userRepository.findById(expert.getId()).orElseThrow();
        row.setExpertStatus(com.bowe.meetstudent.entities.enums.ExpertStatus.REJECTED);
        userRepository.save(row);

        mvc.perform(put("/api/v1/forum/replies/" + r.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("edit")).with(as(expert))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/forum/threads/" + t.getId()).with(as(expert))).andExpect(status().isForbidden());
    }

    @Test
    void hiddenMessagesCannotBeTouchedByTheirAuthor() throws Exception {
        var author = student();
        var t = thread(author, "thread");
        var r = reply(t, author, "hidden one");
        r.setHidden(true);
        replies.save(r);
        mvc.perform(put("/api/v1/forum/replies/" + r.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody("edit")).with(as(author))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/forum/replies/" + r.getId()).with(as(author))).andExpect(status().isNotFound());
        assertThat(replies.count()).isEqualTo(1);
    }

    @Test
    void editingAnUnknownMessageIs404() throws Exception {
        var user = student();
        mvc.perform(put("/api/v1/forum/threads/999999").contentType(MediaType.APPLICATION_JSON)
                .content(threadBody("a", "b", null)).with(as(user))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/forum/replies/999999").contentType(MediaType.APPLICATION_JSON)
                .content(replyBody("b")).with(as(user))).andExpect(status().isNotFound());
    }
}
