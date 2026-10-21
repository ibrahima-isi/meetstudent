package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.entities.enums.ForumReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The administrator's queue of reports: listing, DTO privacy, closing. */
class AdminForumReportsIntegrationTests extends ForumTestSupport {

    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private static final String BASE = "/api/v1/admin/forum/reports";

    @BeforeEach
    void clean() {
        reports.deleteAll();
        replies.deleteAll();
        threads.deleteAll();
    }

    @Test
    void reportsAreReadableByAdminsOnly() throws Exception {
        var t = thread(student(), "t");
        var report = reportOf(t, student(), "spam", Instant.now());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).with(as(student()))).andExpect(status().isForbidden());
        mvc.perform(get(BASE).with(as(approvedExpert()))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + report.getId() + "/handle")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/" + report.getId() + "/handle").with(as(student()))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + report.getId() + "/dismiss").with(as(approvedExpert()))).andExpect(status().isForbidden());
        assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ForumReportStatus.OPEN);
        mvc.perform(get(BASE).with(as(admin()))).andExpect(status().isOk());
    }

    @Test
    void listsNewestFirstWithAFixedOrderAndFiltersByStatus() throws Exception {
        var author = student();
        var base = Instant.parse("2026-10-20T10:00:00Z");
        var t = thread(author, "t");
        var oldest = reportOf(t, student(), "oldest", base);
        var middle = reportOf(t, student(), "middle", base.plus(Duration.ofMinutes(1)));
        var sameInstantLowId = reportOf(t, student(), "tie-low", base.plus(Duration.ofMinutes(2)));
        var sameInstantHighId = reportOf(t, student(), "tie-high", base.plus(Duration.ofMinutes(2)));
        middle.setStatus(ForumReportStatus.HANDLED);
        middle.setHandledAt(base.plus(Duration.ofHours(1)));
        reports.save(middle);

        mvc.perform(get(BASE + "?sort=reason,asc").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].reason").value("tie-high"))
                .andExpect(jsonPath("$.content[1].reason").value("tie-low"))
                .andExpect(jsonPath("$.content[2].reason").value("middle"))
                .andExpect(jsonPath("$.content[3].reason").value("oldest"));
        mvc.perform(get(BASE + "?status=OPEN").with(as(admin())))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[2].reason").value("oldest"));
        mvc.perform(get(BASE + "?status=HANDLED").with(as(admin())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("HANDLED"));
        mvc.perform(get(BASE + "?status=DISMISSED").with(as(admin()))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(BASE + "?status=NOPE").with(as(admin()))).andExpect(status().isBadRequest());
        assertThat(oldest.getId()).isLessThan(sameInstantLowId.getId());
        assertThat(sameInstantLowId.getId()).isLessThan(sameInstantHighId.getId());
    }

    @Test
    void theSizeIsCappedAt100() throws Exception {
        var t = thread(student(), "t");
        for (int i = 0; i < 3; i++) reportOf(t, student(), "r" + i, Instant.now());
        mvc.perform(get(BASE + "?size=100000").with(as(admin())))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void aThreadReportCarriesTheTargetExcerptAndTheReporterFirstNameOnly() throws Exception {
        var reporter = student("Fatou");
        var t = thread(student("Awa"), "Titre du fil");
        reportOf(t, reporter, "Insultes", Instant.now());

        var raw = mvc.perform(get(BASE).with(as(admin())))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].targetType").value("THREAD"))
                .andExpect(jsonPath("$.content[0].targetId").value(t.getId()))
                .andExpect(jsonPath("$.content[0].threadId").value(t.getId()))
                .andExpect(jsonPath("$.content[0].threadTitle").value("Titre du fil"))
                .andExpect(jsonPath("$.content[0].excerpt").value("Body of Titre du fil"))
                .andExpect(jsonPath("$.content[0].targetHidden").value(false))
                .andExpect(jsonPath("$.content[0].reason").value("Insultes"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].reporterName").value("Fatou"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].handledAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("@example.com").doesNotContain("Secret").doesNotContain("password");
    }

    @Test
    void aReplyReportNamesItsThreadAndTruncatesALongExcerptAtACodePointBoundary() throws Exception {
        var t = thread(student(), "Le fil");
        var longBody = "é".repeat(150) + "😀".repeat(100); // 150 chars + 100 emoji (surrogate pairs)
        var r = reply(t, student(), longBody);
        reportOf(r, student(), "Hors sujet", Instant.now());

        var raw = mvc.perform(get(BASE).with(as(admin())))
                .andExpect(jsonPath("$.content[0].targetType").value("REPLY"))
                .andExpect(jsonPath("$.content[0].targetId").value(r.getId()))
                .andExpect(jsonPath("$.content[0].threadId").value(t.getId()))
                .andExpect(jsonPath("$.content[0].threadTitle").value("Le fil"))
                .andReturn().getResponse().getContentAsString();
        var excerpt = json.readTree(raw).at("/content/0/excerpt").asText();
        assertThat(excerpt.codePointCount(0, excerpt.length())).isLessThanOrEqualTo(201);
        assertThat(excerpt).endsWith("…").startsWith("é".repeat(150));
        assertThat(Character.isHighSurrogate(excerpt.charAt(excerpt.length() - 2)) && !Character.isLowSurrogate(excerpt.charAt(excerpt.length() - 1))).isFalse();
        assertThat(excerpt.length()).isLessThan(longBody.length());
    }

    @Test
    void anEditAfterTheReportShowsInTheCurrentExcerptTheReportedExcerptAndTheModificationDate() throws Exception {
        var t = thread(student(), "t");
        var r = reply(t, student(), "original text");
        reportOf(r, student(), "bad", Instant.now());
        mvc.perform(get(BASE).with(as(admin())))
                .andExpect(jsonPath("$.content[0].excerpt").value("original text"))
                .andExpect(jsonPath("$.content[0].reportedExcerpt").value("original text"))
                .andExpect(jsonPath("$.content[0].targetModifiedAt").doesNotExist());

        var row = replies.findById(r.getId()).orElseThrow();
        row.setBody("clean text");
        row.setModifiedAt(Instant.parse("2026-10-21T08:00:00Z"));
        replies.save(row);
        mvc.perform(get(BASE).with(as(admin())))
                .andExpect(jsonPath("$.content[0].excerpt").value("clean text"))
                .andExpect(jsonPath("$.content[0].reportedExcerpt").value("original text"))
                .andExpect(jsonPath("$.content[0].targetModifiedAt").exists());
    }

    @Test
    void theReportedExcerptIsTruncatedLikeTheCurrentOne() throws Exception {
        var t = thread(student(), "t");
        var longText = "x".repeat(800) + "\uD83D\uDE00".repeat(100); // 900 code points, 1000 chars
        var r = reply(t, student(), longText);
        reportOf(r, student(), "bad", Instant.now());
        var raw = mvc.perform(get(BASE).with(as(admin()))).andReturn().getResponse().getContentAsString();
        var node = json.readTree(raw).at("/content/0");
        for (var field : new String[] {"excerpt", "reportedExcerpt"}) {
            var text = node.get(field).asText();
            assertThat(text.codePointCount(0, text.length())).isEqualTo(201);
            assertThat(text).endsWith("…").startsWith("x".repeat(200));
        }
    }

    @Test
    void aShortExcerptIsNotTouchedAndAHiddenTargetStillShowsToTheAdmin() throws Exception {
        var t = thread(student(), "t");
        var r = reply(t, student(), "SECRET-HIDDEN-BODY");
        r.setHidden(true);
        replies.save(r);
        reportOf(r, student(), "bad", Instant.now());
        mvc.perform(get(BASE).with(as(admin())))
                .andExpect(jsonPath("$.content[0].excerpt").value("SECRET-HIDDEN-BODY"))
                .andExpect(jsonPath("$.content[0].targetHidden").value(true));
    }

    @Test
    void aReportWhoseReporterLeftHasNoReporterName() throws Exception {
        var leaver = student();
        var t = thread(student(), "t");
        reportOf(t, leaver, "spam", Instant.now());
        userRepository.deleteById(leaver.getId());
        mvc.perform(get(BASE).with(as(admin())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reporterName").doesNotExist());
    }

    // ---- closing -------------------------------------------------------------------------------

    @Test
    void handleAndDismissCloseAnOpenReportAndDateIt() throws Exception {
        var t = thread(student(), "t");
        var a = reportOf(t, student(), "a", Instant.now());
        var b = reportOf(t, student(), "b", Instant.now());
        var admin = admin();

        mvc.perform(post(BASE + "/" + a.getId() + "/handle").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.getId()))
                .andExpect(jsonPath("$.status").value("HANDLED"))
                .andExpect(jsonPath("$.handledAt").exists());
        mvc.perform(post(BASE + "/" + b.getId() + "/dismiss").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.handledAt").exists());
        mvc.perform(get(BASE + "?status=OPEN").with(as(admin))).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(reports.findById(a.getId()).orElseThrow().getHandledAt()).isNotNull();
    }

    @Test
    void closingRecordsWhichAdminDidItWithoutExposingIt() throws Exception {
        var t = thread(student(), "t");
        var report = reportOf(t, student(), "a", Instant.now());
        var admin = admin();
        var raw = mvc.perform(post(BASE + "/" + report.getId() + "/handle").with(as(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("handledBy").doesNotContain("@example.com");
        var stored = transactionalHandledBy(report.getId());
        assertThat(stored).isEqualTo(admin.getId());

        // a second admin closing it again changes nothing
        var other = admin();
        mvc.perform(post(BASE + "/" + report.getId() + "/dismiss").with(as(other))).andExpect(status().isOk());
        assertThat(transactionalHandledBy(report.getId())).isEqualTo(admin.getId());

        // deleting that admin keeps the report
        userRepository.deleteById(admin.getId());
        assertThat(transactionalHandledBy(report.getId())).isNull();
        assertThat(reports.findById(report.getId())).isPresent();
    }

    private Integer transactionalHandledBy(int reportId) {
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s -> {
            var by = reports.findById(reportId).orElseThrow().getHandledBy();
            return by == null ? null : by.getId();
        });
    }

    @Test
    void closingIsIdempotentAndTheFirstDecisionAndDateStand() throws Exception {
        var t = thread(student(), "t");
        var report = reportOf(t, student(), "a", Instant.now());
        var admin = admin();
        mvc.perform(post(BASE + "/" + report.getId() + "/handle").with(as(admin))).andExpect(status().isOk());
        var firstDate = reports.findById(report.getId()).orElseThrow().getHandledAt();
        while (!Instant.now().isAfter(firstDate)) {
            Thread.onSpinWait(); // the clock must have moved, so a wrongly redated report would differ
        }

        mvc.perform(post(BASE + "/" + report.getId() + "/handle").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HANDLED"));
        mvc.perform(post(BASE + "/" + report.getId() + "/dismiss").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HANDLED"));
        var after = reports.findById(report.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ForumReportStatus.HANDLED);
        assertThat(after.getHandledAt()).isEqualTo(firstDate);
    }

    @Test
    void closingAnUnknownReportIs404() throws Exception {
        mvc.perform(post(BASE + "/987654/handle").with(as(admin()))).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/987654/dismiss").with(as(admin()))).andExpect(status().isNotFound());
    }

    @Test
    void closingAReportDoesNotTouchTheMessage() throws Exception {
        var t = thread(student(), "t");
        var report = reportOf(t, student(), "a", Instant.now());
        mvc.perform(post(BASE + "/" + report.getId() + "/dismiss").with(as(admin()))).andExpect(status().isOk());
        var thread = threads.findById(t.getId()).orElseThrow();
        assertThat(thread.isHidden()).isFalse();
        assertThat(thread.isLocked()).isFalse();
    }
}
