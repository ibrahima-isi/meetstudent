package com.bowe.meetstudent.entities.enums;

import java.util.Set;

public enum MediaCategory {
    DIPLOMA(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"), Extensions.DOCUMENTS),
    CERTIFICATE(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"), Extensions.DOCUMENTS),
    BULLETIN(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"), Extensions.DOCUMENTS),
    PRESENTATION_VIDEO(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"), Extensions.DOCUMENTS),
    SCHOOL_LOGO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN"), Extensions.IMAGES),
    SCHOOL_COVER(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN"), Extensions.IMAGES),
    COURSE_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN"), Extensions.IMAGES),
    PROGRAM_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN"), Extensions.IMAGES),
    USER_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"), Extensions.IMAGES);

    private final MediaVisibility visibility;
    private final boolean moderated;
    private final Set<String> allowedUploadRoles;
    private final Set<String> allowedExtensions;

    MediaCategory(MediaVisibility visibility, boolean moderated, Set<String> allowedUploadRoles,
                  Set<String> allowedExtensions) {
        this.visibility = visibility;
        this.moderated = moderated;
        this.allowedUploadRoles = allowedUploadRoles;
        this.allowedExtensions = allowedExtensions;
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

    /**
     * Lower-case file extensions this category accepts. Public categories are served statically on the
     * main origin, so they take images only; personal documents (private) also take PDF and video.
     */
    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public boolean isPersonalDocument() {
        return moderated && visibility == MediaVisibility.PRIVATE;
    }

    private static final class Extensions {
        static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "webp");
        static final Set<String> DOCUMENTS = Set.of("pdf", "jpg", "jpeg", "png", "webp", "mp4", "webm", "mov");
    }
}
