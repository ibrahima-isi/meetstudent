package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

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
        require("logoMediaId", mediaId, currentId, MediaCategory.SCHOOL_LOGO, "School", "logoMediaId", schoolId, null);
    }

    public void requireSchoolCover(Integer mediaId, Integer currentId, Integer schoolId) {
        require("coverMediaId", mediaId, currentId, MediaCategory.SCHOOL_COVER, "School", "coverMediaId", schoolId, null);
    }

    public void requireProgramPhoto(Integer mediaId, Integer currentId, Integer programId) {
        require("photoMediaId", mediaId, currentId, MediaCategory.PROGRAM_PHOTO, "Program", "photoMediaId", programId, null);
    }

    public void requireCoursePhoto(Integer mediaId, Integer currentId, Integer courseId) {
        require("photoMediaId", mediaId, currentId, MediaCategory.COURSE_PHOTO, "Course", "photoMediaId", courseId, null);
    }

    /**
     * A school about to be created: logo, cover and, because programs and courses cascade with it,
     * every nested photo. The same media may not appear twice in the tree.
     */
    public void requireSchoolTree(School school) {
        Set<Integer> seen = new HashSet<>();
        require("logoMediaId", school.getLogoMediaId(), null, MediaCategory.SCHOOL_LOGO, "School", "logoMediaId", school.getId(), seen);
        require("coverMediaId", school.getCoverMediaId(), null, MediaCategory.SCHOOL_COVER, "School", "coverMediaId", school.getId(), seen);
        if (school.getPrograms() == null) return;
        for (Program program : school.getPrograms()) {
            requireProgramBranch(program, seen);
        }
    }

    /** A program about to be created: its photo and the photos of its nested courses. */
    public void requireProgramTree(Program program) {
        requireProgramBranch(program, new HashSet<>());
    }

    private void requireProgramBranch(Program program, Set<Integer> seen) {
        require("photoMediaId", program.getPhotoMediaId(), null, MediaCategory.PROGRAM_PHOTO, "Program", "photoMediaId", program.getId(), seen);
        if (program.getCourses() == null) return;
        for (Course course : program.getCourses()) {
            require("photoMediaId", course.getPhotoMediaId(), null, MediaCategory.COURSE_PHOTO, "Course", "photoMediaId", course.getId(), seen);
        }
    }

    private void require(String field, Integer mediaId, Integer currentId, MediaCategory expected,
                         String entity, String attribute, Integer selfId, Set<Integer> seen) {
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
        if (seen != null && !seen.add(mediaId)) {
            throw new FieldValidationException(field, "Media " + mediaId + " is used twice in this request");
        }
    }
}
