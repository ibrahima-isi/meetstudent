package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.UserEntity;

/** What the forum shows of an author: first name and a STUDENT/EXPERT label, nothing else. */
final class ForumAuthor {

    private ForumAuthor() {
    }

    static String displayName(UserEntity author) {
        return author == null ? null : author.getFirstname();
    }

    static String role(UserEntity author) {
        if (author == null || author.getRole() == null) return null;
        return switch (author.getRole().getName()) {
            case "ROLE_STUDENT" -> "STUDENT";
            case "ROLE_EXPERT" -> "EXPERT";
            default -> null;
        };
    }
}
