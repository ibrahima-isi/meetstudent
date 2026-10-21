package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.ForumReply;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a reply creation or edit. Surrounding whitespace is removed, nothing else is changed. */
public record ForumReplyRequest(
        @NotBlank(message = "Message vide")
        @Size(max = ForumReply.MAX_BODY, message = "Message trop long (5000 caractères maximum)")
        String body) {

    public ForumReplyRequest {
        body = body == null ? null : body.strip();
    }
}
