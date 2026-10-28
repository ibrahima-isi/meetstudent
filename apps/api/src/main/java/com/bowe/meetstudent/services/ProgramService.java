package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.repositories.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programRepository;
    private final MediaService mediaService;
    private final EntityImageValidator imageValidator;

    /** Blank means "no description" (stored null); otherwise the text is stripped. */
    private static String normalizeDescription(String description) {
        if (description == null) return null;
        var stripped = description.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    @Transactional
    public Program save(Program program) {
        program.setDescription(normalizeDescription(program.getDescription()));
        imageValidator.requireProgramPhoto(program.getPhotoMediaId(), null, program.getId());
        if (program.getCode() != null && (program.getId() == null
                ? programRepository.existsByCodeIgnoreCase(program.getCode())
                : programRepository.existsByCodeIgnoreCaseAndIdNot(program.getCode(), program.getId()))) {
            throw new ConflictException("A program with this code already exists");
        }
        return this.programRepository.save(program);
    }

    public Page<Program> findAll(Pageable pageable) {
        return this.programRepository.findAll(pageable);
    }

    public Optional<Program> findById(int id) {
        return this.programRepository.findById(id);
    }

    public Page<Program> findByName(String name, Pageable pageable) {
        String safeName = (name == null) ? "" : name;
        return this.programRepository.findByNameContainingIgnoreCase(safeName, pageable);
    }

    public boolean exists(int id) {
        return this.programRepository.existsById(id);
    }

    /** Photo ids of a program and of its courses (null entries are skipped by the media service). */
    static List<Integer> photoIdsOf(Program program) {
        List<Integer> ids = new java.util.ArrayList<>();
        ids.add(program.getPhotoMediaId());
        if (program.getCourses() != null) {
            program.getCourses().forEach(course -> ids.add(course.getPhotoMediaId()));
        }
        return ids;
    }

    @Transactional
    public void delete(int id) {
        this.programRepository.findById(id).ifPresent(program -> {
            List<Integer> mediaIds = photoIdsOf(program);
            this.programRepository.deleteWishlistLinks(id);
            this.programRepository.deleteById(id);
            // Files go only after the commit; the courses (JPA cascade) take their photos with them.
            mediaService.deleteByIdsAfterCommit(mediaIds);
        });
    }

    public Page<Program> findAllOrderByRateDesc(Pageable pageable) {
        return programRepository.findAllByOrderByAverageRateDesc(pageable);
    }

    public Page<Program> findAllOrderByRateAsc(Pageable pageable) {
        return programRepository.findAllByOrderByAverageRateAsc(pageable);
    }

    public List<Program> findProgramByAccreditationCode(String code) {
        String safeCode = (code == null) ? "" : code;
        return programRepository.findProgramByProgramAccreditationsContainingIgnoreCase(safeCode);
    }

    @Transactional
    public Program patch(Integer id, Program updates) {
        return programRepository.findById(id).map(existing -> {
            if (updates.getCode() != null && programRepository.existsByCodeIgnoreCaseAndIdNot(updates.getCode(), id)) {
                throw new ConflictException("A program with this code already exists");
            }
            imageValidator.requireProgramPhoto(updates.getPhotoMediaId(), existing.getPhotoMediaId(), id);
            if (updates.getPhotoMediaId() != null
                    &&!updates.getPhotoMediaId().equals(existing.getPhotoMediaId())) {
                mediaService.deleteById(existing.getPhotoMediaId());
            }

            if (updates.getName() != null) existing.setName(updates.getName());
            if (updates.getCode() != null) existing.setCode(updates.getCode());
            if (updates.getDuration() != null) existing.setDuration(updates.getDuration());
            // null keeps; '' or blank clears (stored null)
            if (updates.getDescription() != null) existing.setDescription(normalizeDescription(updates.getDescription()));
            if (updates.getAvailableSeats() != null) existing.setAvailableSeats(updates.getAvailableSeats());
            if (updates.getAnnualFee() != null) existing.setAnnualFee(updates.getAnnualFee());
            if (updates.getLevel() != null) existing.setLevel(updates.getLevel());
            if (updates.getPhotoMediaId() != null) existing.setPhotoMediaId(updates.getPhotoMediaId());
            if (updates.getSchool() != null) existing.setSchool(updates.getSchool());
            
            return programRepository.save(existing);
        }).orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("Program not found"));
    }
}
