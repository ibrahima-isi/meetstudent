package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guards the media ids an admin can attach to a school, program or course. The id must designate
 * an existing, public, ownerless media of the expected category that no other entity uses:
 * entity images are deleted with (or when replaced on) their entity, so pointing at a personal
 * document or at somebody else's image would destroy it, and a private document would be
 * exposed through the entity's public media object.
 * <p>
 * Ownerless: {@code MediaService.upload} only assigns an owner to personal documents and user
 * photos, so every genuine admin upload of an entity-image category has no owner.
 */
@Component
@RequiredArgsConstructor
public class EntityImageValidator {

    private final MediaService mediaService;

    @PersistenceContext
    private EntityManager entityManager;

    /** {@code currentId} is the value already stored (an unchanged value is left alone: legacy data keeps working). */
    public void requireSchoolLogo(Integer mediaId, Integer currentId, Integer schoolId) {
        require("logoMediaId", mediaId, currentId, MediaCategory.SCHOOL_LOGO, "School", "logoMediaId", schoolId);
    }

    public void requireSchoolCover(Integer mediaId, Integer currentId, Integer schoolId) {
        require("coverMediaId", mediaId, currentId, MediaCategory.SCHOOL_COVER, "School", "coverMediaId", schoolId);
    }

    public void requireProgramPhoto(Integer mediaId, Integer currentId, Integer programId) {
        require("photoMediaId", mediaId, currentId, MediaCategory.PROGRAM_PHOTO, "Program", "photoMediaId", programId);
    }

    public void requireCoursePhoto(Integer mediaId, Integer currentId, Integer courseId) {
        require("photoMediaId", mediaId, currentId, MediaCategory.COURSE_PHOTO, "Course", "photoMediaId", courseId);
    }

    private void require(String field, Integer mediaId, Integer currentId, MediaCategory expected,
                         String entity, String attribute, Integer selfId) {
        if (mediaId == null || mediaId.equals(currentId)) return;
        Media media = mediaService.findById(mediaId)
                .orElseThrow(() -> new FieldValidationException(field, "Unknown media " + mediaId));
        if (media.getCategory() != expected
                || media.getVisibility() != MediaVisibility.PUBLIC
                || media.getOwnerId() != null) {
            throw new FieldValidationException(field, "Media " + mediaId + " is not a " + expected + " image");
        }
        // Entity names and attributes are constants of this class, never user input.
        Long users = entityManager.createQuery(
                        "select count(e) from " + entity + " e where e." + attribute + " = :media and e.id <> :self",
                        Long.class)
                .setParameter("media", mediaId)
                .setParameter("self", selfId == null ? -1 : selfId)
                .getSingleResult();
        if (users > 0) {
            throw new FieldValidationException(field, "Media " + mediaId + " is already used by another entity");
        }
    }
}
