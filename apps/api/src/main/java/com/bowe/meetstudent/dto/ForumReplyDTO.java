package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.ForumReply;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Public view of a reply; same privacy rules as {@link ForumThreadDTO}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForumReplyDTO(
        Integer id,
        Integer threadId,
        String body,
        String authorName,
        String authorRole,
        boolean authorDeleted,
        boolean mine,
        boolean hidden,
        Instant createdAt,
        Instant modifiedAt) {

    public static ForumReplyDTO of(ForumReply r, Integer viewerId) {
        var author = r.getAuthor();
        return new ForumReplyDTO(r.getId(), r.getThread().getId(), r.getBody(),
                ForumAuthor.displayName(author), ForumAuthor.role(author), author == null,
                author != null && author.getId().equals(viewerId), r.isHidden(), r.getCreatedAt(), r.getModifiedAt());
    }
}
