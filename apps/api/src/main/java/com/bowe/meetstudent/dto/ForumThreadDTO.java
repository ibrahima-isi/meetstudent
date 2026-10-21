package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.ForumThread;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Public view of a thread. Deliberately exposes no e-mail, last name or user id: the author is the first name only
 * ({@code authorName}, absent when the account was deleted, in which case {@code authorDeleted} is true and the
 * client shows its own "deleted user" text) and {@code mine} says whether the caller wrote it.
 * {@code hidden} is true only for administrators, who are the only ones to ever receive a hidden thread.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForumThreadDTO(
        Integer id,
        String title,
        String body,
        Integer schoolId,
        String schoolName,
        String authorName,
        String authorRole,
        boolean authorDeleted,
        boolean mine,
        boolean locked,
        boolean hidden,
        long replyCount,
        Instant createdAt,
        Instant modifiedAt) {

    public static ForumThreadDTO of(ForumThread t, long replyCount, Integer viewerId) {
        var author = t.getAuthor();
        var school = t.getSchool();
        return new ForumThreadDTO(t.getId(), t.getTitle(), t.getBody(),
                school == null ? null : school.getId(), school == null ? null : school.getName(),
                ForumAuthor.displayName(author), ForumAuthor.role(author), author == null,
                author != null && author.getId().equals(viewerId),
                t.isLocked(), t.isHidden(), replyCount, t.getCreatedAt(), t.getModifiedAt());
    }
}
