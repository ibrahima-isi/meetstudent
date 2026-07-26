package com.bowe.meetstudent.entities.enums;

import java.util.Set;

public enum MediaCategory {
    DIPLOMA(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    CERTIFICATE(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    BULLETIN(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    PRESENTATION_VIDEO(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    SCHOOL_LOGO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
    SCHOOL_COVER(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
    COURSE_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
    PROGRAM_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
    USER_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"));

    private final MediaVisibility visibility;
    private final boolean moderated;
    private final Set<String> allowedUploadRoles;

    MediaCategory(MediaVisibility visibility, boolean moderated, Set<String> allowedUploadRoles) {
        this.visibility = visibility;
        this.moderated = moderated;
        this.allowedUploadRoles = allowedUploadRoles;
    }

    public MediaVisibility getVisibility() {
        return visibility;
    }

    public boolean isModerated() {
        return moderated;
    }

    public Set<String> getAllowedUploadRoles() {
        return allowedUploadRoles;
    }

    public boolean isPersonalDocument() {
        return moderated && visibility == MediaVisibility.PRIVATE;
    }
}
