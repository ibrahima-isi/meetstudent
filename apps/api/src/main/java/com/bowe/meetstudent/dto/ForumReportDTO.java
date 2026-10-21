package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.ForumReport;
import com.bowe.meetstudent.entities.enums.ForumReportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * A report as the administrator's queue sees it. Reporters are personal data: only their first name is shown (absent
 * once the account is deleted), never an e-mail, last name or user id. The excerpt is the start of the reported
 * message now ({@code excerpt}) and as it was when reported ({@code reportedExcerpt}, from the stored snapshot), both 200
 * code points at most, with {@code targetModifiedAt}: a target edited after the report shows a different excerpt and a
 * modification date. {@code targetHidden} says whether the message is currently hidden. Who closed the report is not shown.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForumReportDTO(
        Integer id,
        String targetType,
        Integer targetId,
        Integer threadId,
        String threadTitle,
        String excerpt,
        String reportedExcerpt,
        Instant targetModifiedAt,
        boolean targetHidden,
        String reason,
        ForumReportStatus status,
        String reporterName,
        Instant createdAt,
        Instant handledAt) {

    public static final int EXCERPT_LENGTH = 200;

    public static ForumReportDTO of(ForumReport r) {
        var reply = r.getReply();
        var thread = reply == null ? r.getThread() : reply.getThread();
        var text = reply == null ? thread.getBody() : reply.getBody();
        return new ForumReportDTO(r.getId(), reply == null ? "THREAD" : "REPLY",
                reply == null ? thread.getId() : reply.getId(), thread.getId(), thread.getTitle(),
                excerpt(text), excerpt(r.getTargetSnapshot()), reply == null ? thread.getModifiedAt() : reply.getModifiedAt(),
                reply == null ? thread.isHidden() : reply.isHidden(), r.getReason(), r.getStatus(),
                ForumAuthor.displayName(r.getReporter()), r.getCreatedAt(), r.getHandledAt());
    }

    static String excerpt(String text) {
        if (text.codePointCount(0, text.length()) <= EXCERPT_LENGTH) return text;
        return text.substring(0, text.offsetByCodePoints(0, EXCERPT_LENGTH)) + "…";
    }
}
