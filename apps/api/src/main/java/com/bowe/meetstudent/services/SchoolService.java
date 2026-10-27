package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.repositories.TagRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final MediaService mediaService;
    private final TagRepository tagRepository;
    private final MediaRepository mediaRepository;
    private final MediaStorageService storageService;

    public School save(School school) {
        requireExistingTags(school);
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
     * Wishlist links are cleared first (the FK has no cascade). The media rows go with the school,
     * but the stored files only after the transaction commits, so a failed delete never leaves the
     * school pointing at images that are already gone.
     */
    @Transactional
    public void delete(int id){
        this.schoolRepository.findById(id).ifPresent(school -> {
            List<Media> media = Stream.of(school.getLogoMediaId(), school.getCoverMediaId())
                    .filter(Objects::nonNull)
                    .map(mediaRepository::findById)
                    .flatMap(Optional::stream)
                    .toList();
            this.schoolRepository.deleteWishlistLinks(id);
            this.schoolRepository.deleteById(id);
            this.schoolRepository.flush();
            media.forEach(mediaRepository::delete);
            deleteFilesAfterCommit(media.stream().map(Media::getStorageKey).toList());
        });
    }

    private void deleteFilesAfterCommit(List<String> keys) {
        if (keys.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteFilesQuietly(keys);
                }
            });
        } else {
            deleteFilesQuietly(keys);
        }
    }

    private void deleteFilesQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (IOException | RuntimeException e) {
                log.warn("Could not delete stored file for media key {}: {}", key, e.toString());
            }
        }
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
