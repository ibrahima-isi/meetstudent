package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.ForumThread;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a thread creation or edit. Surrounding whitespace is removed (a text of spaces only is blank), nothing
 * else is changed: the text is stored as typed. {@code schoolId} is only read on creation (a thread keeps its school).
 */
public record ForumThreadRequest(
        @NotBlank(message = "Titre vide")
        @Size(max = ForumThread.MAX_TITLE, message = "Titre trop long (150 caractères maximum)")
        String title,
        @NotBlank(message = "Message vide")
        @Size(max = ForumThread.MAX_BODY, message = "Message trop long (5000 caractères maximum)")
        String body,
        Integer schoolId) {

    public ForumThreadRequest {
        title = title == null ? null : title.strip();
        body = body == null ? null : body.strip();
    }
}
