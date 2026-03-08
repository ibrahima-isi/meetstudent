package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.repositories.SchoolRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchoolRateService {
    private final SchoolRateRepository schoolRateRepository;

    public SchoolRate save(SchoolRate schoolRate) {
        return this.schoolRateRepository.save(schoolRate);
    }

    public List<SchoolRate> findBySchoolId(Integer schoolId) {
        return schoolRateRepository.findBySchoolId(schoolId);
    }

    public Double getAverageNoteBySchoolId(Integer schoolId) {
        return schoolRateRepository.getAverageNoteBySchoolId(schoolId);
    }
}
