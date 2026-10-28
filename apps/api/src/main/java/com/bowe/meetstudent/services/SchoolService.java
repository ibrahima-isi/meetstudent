package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.repositories.ProgramRepository;
import com.bowe.meetstudent.repositories.TagRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final MediaService mediaService;
    private final TagRepository tagRepository;
    private final ProgramRepository programRepository;
    private final EntityImageValidator imageValidator;

    public School save(School school) {
        requireExistingTags(school);
        requireValidImages(school);
        return this.schoolRepository.save(school);
    }

    public List<School> getSchools() {
        return this.schoolRepository.findAll();
    }

    public Page<School> findAll(Pageable pageable){
        return this.schoolRepository.findAll(pageable);
    }

    public Optional<School> getSchoolById(int id){
        return this.schoolRepository.findById(id);
    }

    /**
     * Wishlist links (school and program) are cleared first, the FKs have no cascade on every
     * database. The media rows go with the school, the stored files only after the transaction
     * commits, so a failed delete never leaves the school pointing at images that are gone.
     */
    @Transactional
    public void delete(int id){
        this.schoolRepository.findById(id).ifPresent(school -> {
            // Programs and courses go with the school (JPA cascade): so must their photos.
            List<Integer> mediaIds = new ArrayList<>(Arrays.asList(school.getLogoMediaId(), school.getCoverMediaId()));
            if (school.getPrograms() != null) {
                school.getPrograms().forEach(program -> mediaIds.addAll(ProgramService.photoIdsOf(program)));
            }
            List<Integer> programIds = school.getPrograms() == null ? List.of()
                    : school.getPrograms().stream().map(Program::getId).toList();
            programIds.forEach(this.programRepository::deleteWishlistLinks);
            this.schoolRepository.deleteWishlistLinks(id);
            this.schoolRepository.deleteById(id);
            this.schoolRepository.flush();
            mediaService.deleteByIdsAfterCommit(mediaIds);
        });
    }

    public Boolean exists(int id){
        return this.schoolRepository.existsById(id);
    }

    public Page<School> getSchoolByName(String name, Pageable pageable) {
        String safeName = (name == null) ? "" : name;
        return this.schoolRepository.findSchoolByNameContainingIgnoreCase(safeName, pageable);
    }

    public Page<School> findSchoolByCity(String city, Pageable pageable) {
        String safeCity = (city == null) ? "" : city;
        return this.schoolRepository.findSchoolByAddress_City(safeCity, pageable);
    }

    public Page<School> findSchoolByCountry(String country, Pageable pageable) {
        String safeCountry = (country == null) ? "" : country;
        return this.schoolRepository.findSchoolByAddress_Country(safeCountry, pageable);
    }

    public Page<School> searchSchools(String city, String country, String programName, String tag, Pageable pageable) {
        String safeCity = (city == null) ? "" : city;
        String safeCountry = (country == null) ? "" : country;
        String safeProgramName = (programName == null) ? "" : programName;
        String safeTag = (tag == null) ? "" : tag;
        return this.schoolRepository.searchSchools(safeCity, safeCountry, safeProgramName, safeTag, pageable);
    }

    public Page<School> findAllOrderByRateDesc(Pageable pageable) {
        return schoolRepository.findAllByOrderByAverageRateDesc(pageable);
    }

    public Page<School> findAllOrderByRateAsc(Pageable pageable) {
        return schoolRepository.findAllByOrderByAverageRateAsc(pageable);
    }

    @Transactional
    public School patch(Integer id, School updates) {
        return schoolRepository.findById(id).map(existing -> {
            requireExistingTags(updates);
            imageValidator.requireSchoolLogo(updates.getLogoMediaId(), existing.getLogoMediaId(), id);
            imageValidator.requireSchoolCover(updates.getCoverMediaId(), existing.getCoverMediaId(), id);
            // Check for media changes before updating
            if (updates.getLogoMediaId() != null
                    && !updates.getLogoMediaId().equals(existing.getLogoMediaId())) {
                mediaService.deleteById(existing.getLogoMediaId());
            }
            if (updates.getCoverMediaId() != null
                    && !updates.getCoverMediaId().equals(existing.getCoverMediaId())) {
                mediaService.deleteById(existing.getCoverMediaId());
            }

            // Map remaining fields
            if (updates.getName() != null) existing.setName(updates.getName());
            if (updates.getCode() != null) existing.setCode(updates.getCode());
            if (updates.getCreation() != null) existing.setCreation(updates.getCreation());
            if (updates.getAddress() != null) existing.setAddress(updates.getAddress());
            if (updates.getLogoMediaId() != null) existing.setLogoMediaId(updates.getLogoMediaId());
            if (updates.getCoverMediaId() != null) existing.setCoverMediaId(updates.getCoverMediaId());
            if (updates.getTags() != null) existing.setTags(updates.getTags());
            
            return schoolRepository.save(existing);
        }).orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("School not found"));
    }

    /** Logo, cover and, because programs and courses cascade with the school, their photos. */
    private void requireValidImages(School school) {
        imageValidator.requireSchoolLogo(school.getLogoMediaId(), null, school.getId());
        imageValidator.requireSchoolCover(school.getCoverMediaId(), null, school.getId());
        if (school.getPrograms() == null) return;
        for (var program : school.getPrograms()) {
            imageValidator.requireProgramPhoto(program.getPhotoMediaId(), null, program.getId());
            if (program.getCourses() == null) continue;
            for (var course : program.getCourses()) {
                imageValidator.requireCoursePhoto(course.getPhotoMediaId(), null, course.getId());
            }
        }
    }

    /** An unknown tag id in the body is a client error, not a foreign-key failure at flush time. */
    private void requireExistingTags(School school) {
        if (school.getTags() == null || school.getTags().isEmpty()) return;
        var ids = new java.util.LinkedHashSet<Integer>();
        for (var tag : school.getTags()) {
            if (tag == null || tag.getId() == null) throw new FieldValidationException("tags", "Unknown tag");
            ids.add(tag.getId());
        }
        var found = new java.util.HashSet<Integer>();
        tagRepository.findAllById(ids).forEach(t -> found.add(t.getId()));
        for (var id : ids) {
            if (!found.contains(id)) throw new FieldValidationException("tags", "Unknown tag " + id);
        }
    }
}
