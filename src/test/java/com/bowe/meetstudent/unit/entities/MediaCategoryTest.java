package com.bowe.meetstudent.unit.entities;

import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaCategoryTest {

    @Test
    void personalDocumentsArePrivateModeratedAndUploadableByStudentExpertAdmin() {
        for (MediaCategory c : new MediaCategory[]{
                MediaCategory.DIPLOMA, MediaCategory.CERTIFICATE,
                MediaCategory.BULLETIN, MediaCategory.PRESENTATION_VIDEO}) {
            assertEquals(MediaVisibility.PRIVATE, c.getVisibility(), c.name());
            assertTrue(c.isModerated(), c.name());
            assertTrue(c.isPersonalDocument(), c.name());
            assertEquals(
                    java.util.Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"),
                    c.getAllowedUploadRoles(), c.name());
        }
    }

    @Test
    void schoolMediaIsPublicAndAdminOnly() {
        for (MediaCategory c : new MediaCategory[]{MediaCategory.SCHOOL_LOGO, MediaCategory.SCHOOL_COVER}) {
            assertEquals(MediaVisibility.PUBLIC, c.getVisibility(), c.name());
            assertFalse(c.isModerated(), c.name());
            assertFalse(c.isPersonalDocument(), c.name());
            assertEquals(java.util.Set.of("ROLE_ADMIN"), c.getAllowedUploadRoles(), c.name());
        }
    }

    @Test
    void userPhotoIsPublicAndUploadableByAnyAuthenticatedRole() {
        assertEquals(MediaVisibility.PUBLIC, MediaCategory.USER_PHOTO.getVisibility());
        assertFalse(MediaCategory.USER_PHOTO.isModerated());
        assertEquals(
                java.util.Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"),
                MediaCategory.USER_PHOTO.getAllowedUploadRoles());
    }

    @Test
    void coursePhotoAndProgramPhotoArePublicNonModeratedAdminOnly() {
        for (MediaCategory c : new MediaCategory[]{MediaCategory.COURSE_PHOTO, MediaCategory.PROGRAM_PHOTO}) {
            assertEquals(MediaVisibility.PUBLIC, c.getVisibility(), c.name());
            assertFalse(c.isModerated(), c.name());
            assertFalse(c.isPersonalDocument(), c.name());
            assertEquals(java.util.Set.of("ROLE_ADMIN"), c.getAllowedUploadRoles(), c.name());
        }
    }
}
