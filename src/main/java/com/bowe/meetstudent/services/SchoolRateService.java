package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.repositories.SchoolRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SchoolRateService {
    private final SchoolRateRepository schoolRateRepository;

    @Transactional
    public SchoolRate save(SchoolRate schoolRate) {
        return this.schoolRateRepository.save(schoolRate);
    }

    public List<SchoolRate> findBySchoolId(Integer schoolId, org.springframework.data.domain.Sort sort) {
        return schoolRateRepository.findBySchoolId(schoolId, sort);
    }

    public Double getAverageNoteBySchoolId(Integer schoolId) {
        return schoolRateRepository.getAverageNoteBySchoolId(schoolId);
    }

    public Optional<SchoolRate> findById(Integer id) {
        return schoolRateRepository.findById(id);
    }

    public Page<SchoolRate> findAll(Pageable pageable) {
        return schoolRateRepository.findAll(pageable);
    }

    @Transactional
    public void delete(Integer id) {
        schoolRateRepository.deleteById(id);
    }
}
