package com.bowe.meetstudent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The address a pending email change is waiting on; absent from the JSON when nothing is pending. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PendingEmailResponse(String pendingEmail) {
}
