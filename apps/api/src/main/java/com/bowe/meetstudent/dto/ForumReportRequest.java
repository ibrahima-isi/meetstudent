package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.ForumReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a report. The reason is required; surrounding whitespace is removed, nothing else is changed. */
public record ForumReportRequest(
        @NotBlank(message = "Motif obligatoire")
        @Size(max = ForumReport.MAX_REASON, message = "Motif trop long (255 caractères maximum)")
        String reason) {

    public ForumReportRequest {
        reason = reason == null ? null : reason.strip();
    }
}
