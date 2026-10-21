package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.entities.ForumThread;
import com.bowe.meetstudent.entities.enums.ForumReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reporting a message: who may, one report per user and message, hidden/unknown/own messages, validation, budget. */
class ForumReportIntegrationTests extends ForumTestSupport {

    @BeforeEach
    void clean() {
        reports.deleteAll();
        replies.deleteAll();
        threads.deleteAll();
    }

    private org.springframework.test.web.servlet.ResultActions reportThread(com.bowe.meetstudent.entities.UserEntity who, int id, String reason) throws Exception {
        var request = post("/api/v1/forum/threads/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content(reasonBody(reason));
        if (who != null) request.with(as(who));
        return mvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions reportReply(com.bowe.meetstudent.entities.UserEntity who, int id, String reason) throws Exception {
        var request = post("/api/v1/forum/replies/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content(reasonBody(reason));
        if (who != null) request.with(as(who));
        return mvc.perform(request);
    }

    // ---- who ----------------------------------------------------------------------------------

    @Test
    void anyone_signed_in_except_an_admin_can_report_even_a_pending_expert() throws Exception {
        var t = thread(student(), "reported");
        var r = reply(t, student(), "a reply");
        reportThread(null, t.getId(), "spam").andExpect(status().isUnauthorized());
        reportReply(null, r.getId(), "spam").andExpect(status().isUnauthorized());
        reportThread(admin(), t.getId(), "spam").andExpect(status().isForbidden());
        reportReply(admin(), r.getId(), "spam").andExpect(status().isForbidden());
        assertThat(reports.count()).isZero();

        reportThread(student(), t.getId(), "spam").andExpect(status().isCreated());
        reportThread(approvedExpert(), t.getId(), "spam").andExpect(status().isCreated());
        reportThread(pendingExpert(), t.getId(), "spam").andExpect(status().isCreated());
        reportReply(rejectedExpert(), r.getId(), "spam").andExpect(status().isCreated());
        assertThat(reports.count()).isEqualTo(4);
    }

    @Test
    void aReportIsStoredOpenWithItsReporterReasonAndDate() throws Exception {
        var reporter = student();
        var t = thread(student(), "reported");
        var r = reply(t, student(), "a reply");
        reportThread(reporter, t.getId(), "  Insultes  ").andExpect(status().isCreated());
        reportReply(reporter, r.getId(), "Hors sujet").andExpect(status().isCreated());

        var all = reports.findAll();
        assertThat(all).hasSize(2);
        var onThread = all.stream().filter(x -> x.getThread() != null).findFirst().orElseThrow();
        var onReply = all.stream().filter(x -> x.getReply() != null).findFirst().orElseThrow();
        assertThat(onThread.getReason()).isEqualTo("Insultes");
        assertThat(onThread.getStatus()).isEqualTo(ForumReportStatus.OPEN);
        assertThat(onThread.getCreatedAt()).isNotNull();
        assertThat(onThread.getHandledAt()).isNull();
        assertThat(onThread.getReply()).isNull();
        assertThat(onReply.getThread()).isNull();
        assertThat(onThread.getReporter().getId()).isEqualTo(reporter.getId());
    }

    @Test
    void theReportKeepsASnapshotOfTheTextAsItWasAtReportTime() throws Exception {
        var author = student();
        var t = thread(author, "reported");
        var r = reply(t, author, "é".repeat(1200)); // longer than the 1000-character snapshot
        reportThread(student(), t.getId(), "spam").andExpect(status().isCreated());
        reportReply(student(), r.getId(), "spam").andExpect(status().isCreated());
        // the author edits afterwards
        var row = threads.findById(t.getId()).orElseThrow();
        row.setBody("rewritten after the report");
        threads.save(row);

        var all = reports.findAll();
        var onThread = all.stream().filter(x -> x.getThread() != null).findFirst().orElseThrow();
        var onReply = all.stream().filter(x -> x.getReply() != null).findFirst().orElseThrow();
        assertThat(onThread.getTargetSnapshot()).isEqualTo("Body of reported");
        assertThat(onReply.getTargetSnapshot()).isEqualTo("é".repeat(1000));
    }

    @Test
    void theResponseNeverEchoesAnythingBack() throws Exception {
        var t = thread(student(), "reported");
        var body = reportThread(student(), t.getId(), "spam").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).isEmpty();
    }

    // ---- one per user and message --------------------------------------------------------------

    @Test
    void reportingTheSameMessageTwiceIs409AndStoresNothingMore() throws Exception {
        var reporter = student();
        var t = thread(student(), "reported");
        var r = reply(t, student(), "a reply");
        reportThread(reporter, t.getId(), "spam").andExpect(status().isCreated());
        reportThread(reporter, t.getId(), "autre motif").andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        reportReply(reporter, r.getId(), "spam").andExpect(status().isCreated());
        reportReply(reporter, r.getId(), "autre motif").andExpect(status().isConflict());
        assertThat(reports.count()).isEqualTo(2);

        // another user, and the same user on another message, are fine
        reportThread(student(), t.getId(), "spam").andExpect(status().isCreated());
        reportThread(reporter, thread(student(), "other").getId(), "spam").andExpect(status().isCreated());
    }

    @Test
    void aClosedReportStillBlocksTheSameUserFromReportingAgain() throws Exception {
        var reporter = student();
        var t = thread(student(), "reported");
        var done = reportOf(t, reporter, "old", Instant.now());
        done.setStatus(ForumReportStatus.DISMISSED);
        done.setHandledAt(Instant.now());
        reports.save(done);
        reportThread(reporter, t.getId(), "again").andExpect(status().isConflict());
    }

    @Test
    void youCannotReportYourOwnMessage() throws Exception {
        var me = student();
        var t = thread(me, "mine");
        var r = reply(t, me, "my reply");
        reportThread(me, t.getId(), "spam").andExpect(status().isConflict());
        reportReply(me, r.getId(), "spam").andExpect(status().isConflict());
        assertThat(reports.count()).isZero();
    }

    @Test
    void aMessageWhoseAuthorLeftCanStillBeReported() throws Exception {
        var t = thread(null, "orphan");
        var r = reply(t, null, "orphan reply");
        reportThread(student(), t.getId(), "spam").andExpect(status().isCreated());
        reportReply(student(), r.getId(), "spam").andExpect(status().isCreated());
    }

    // ---- unknown and hidden --------------------------------------------------------------------

    @Test
    void unknownMessagesAre404() throws Exception {
        reportThread(student(), 987654, "spam").andExpect(status().isNotFound());
        reportReply(student(), 987654, "spam").andExpect(status().isNotFound());
    }

    @Test
    void hiddenMessagesAreInvisibleSoReportingThemIs404() throws Exception {
        var author = student();
        var hiddenThread = thread(author, "hidden thread");
        hiddenThread.setHidden(true);
        threads.save(hiddenThread);
        var replyInHiddenThread = reply(hiddenThread, author, "reply of a hidden thread");
        var visible = thread(author, "visible");
        var hiddenReply = reply(visible, author, "hidden reply");
        hiddenReply.setHidden(true);
        replies.save(hiddenReply);

        reportThread(student(), hiddenThread.getId(), "spam").andExpect(status().isNotFound());
        reportReply(student(), replyInHiddenThread.getId(), "spam").andExpect(status().isNotFound());
        reportReply(student(), hiddenReply.getId(), "spam").andExpect(status().isNotFound());
        assertThat(reports.count()).isZero();
    }

    // ---- validation ----------------------------------------------------------------------------

    @Test
    void theReasonIsRequiredAtMost255CharactersAndSafeText() throws Exception {
        var t = thread(student(), "reported");
        var reporter = student();
        reportThread(reporter, t.getId(), "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").exists());
        reportThread(reporter, t.getId(), "x".repeat(256)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").exists());
        reportThread(reporter, t.getId(), "bad\u0000reason").andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").exists());
        reportThread(reporter, t.getId(), "zero​width").andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").exists());
        reportThread(reporter, t.getId(), "fillerㅤ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").exists());
        mvc.perform(post("/api/v1/forum/threads/" + t.getId() + "/report").contentType(MediaType.APPLICATION_JSON)
                .content("{}").with(as(reporter))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").exists());
        mvc.perform(post("/api/v1/forum/threads/" + t.getId() + "/report").contentType(MediaType.APPLICATION_JSON)
                .with(as(reporter))).andExpect(status().isBadRequest());
        assertThat(reports.count()).isZero();

        reportThread(reporter, t.getId(), "x".repeat(255)).andExpect(status().isCreated());
    }

    @Test
    void aTagLikeReasonIsStoredAsTyped() throws Exception {
        var t = thread(student(), "reported");
        reportThread(student(), t.getId(), "<script>alert(1)</script>").andExpect(status().isCreated());
        assertThat(reports.findAll().get(0).getReason()).isEqualTo("<script>alert(1)</script>");
    }

    // ---- budget --------------------------------------------------------------------------------

    @Test
    void reportsHaveTheirOwnBudgetOfTenPerHourAndPerUser() throws Exception {
        var spammer = student();
        var author = student();
        for (int i = 0; i < 10; i++) {
            reportThread(spammer, thread(author, "t" + i).getId(), "spam").andExpect(status().isCreated());
        }
        var refused = reportThread(spammer, thread(author, "t11").getId(), "spam")
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.status").value(429))
                .andReturn().getResponse();
        assertThat(Long.parseLong(refused.getHeader("Retry-After"))).isBetween(1L, 3600L);
        assertThat(reports.count()).isEqualTo(10);
        // another user is not affected
        reportThread(student(), thread(author, "t12").getId(), "spam").andExpect(status().isCreated());
    }

    @Test
    void refusedReportsDoNotConsumeTheBudgetAndWritesAndReportsDoNotShareTheirs() throws Exception {
        var user = student();
        var author = student();
        var t = thread(author, "target");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/forum/threads").contentType(MediaType.APPLICATION_JSON)
                    .content(threadBody("w" + i, "b", null)).with(as(user))).andExpect(status().isCreated());
        }
        // the write budget is spent, reporting still works...
        reportThread(user, t.getId(), "spam").andExpect(status().isCreated());
        // ...and duplicates / unknown targets do not eat the report budget
        for (int i = 0; i < 12; i++) {
            reportThread(user, t.getId(), "spam").andExpect(status().isConflict());
            reportThread(user, 987654, "spam").andExpect(status().isNotFound());
        }
        for (int i = 0; i < 9; i++) {
            reportThread(user, thread(author, "n" + i).getId(), "spam").andExpect(status().isCreated());
        }
    }

    // ---- life cycle ----------------------------------------------------------------------------

    @Test
    void deletingTheReporterKeepsTheReportWithoutReporter() throws Exception {
        var leaver = student();
        var t = thread(student(), "reported");
        reportThread(leaver, t.getId(), "spam").andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/users/" + leaver.getId()).with(as(leaver)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD)))).andExpect(status().isNoContent());
        var kept = reports.findAll();
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).getReason()).isEqualTo("spam");
        assertThat(reportReporterId()).isNull();
    }

    private Integer reportReporterId() {
        var reporter = reports.findAll().get(0).getReporter();
        return reporter == null ? null : reporter.getId();
    }

    @Test
    void reportsGoWithTheirMessageThroughAnAuthorDeletion() throws Exception {
        var author = student();
        var t = thread(author, "going");
        var r = reply(t, student(), "reply");
        reportOf(t, student(), "on thread", Instant.now());
        reportOf(r, student(), "on reply", Instant.now());
        var other = thread(student(), "staying");
        reportOf(other, student(), "stays", Instant.now());

        mvc.perform(delete("/api/v1/forum/threads/" + t.getId()).with(as(author))).andExpect(status().isNoContent());

        assertThat(reports.findAll()).extracting(x -> x.getReason()).containsExactly("stays");
        assertThat(threads.findById(other.getId())).map(ForumThread::getTitle).contains("staying");
    }
}
