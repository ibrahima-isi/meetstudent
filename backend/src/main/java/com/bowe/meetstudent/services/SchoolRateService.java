package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.repositories.SchoolRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchoolRateService {
    private final SchoolRateRepository schoolRateRepository;

    public SchoolRate save(SchoolRate schoolRate) {
        return this.schoolRateRepository.save(schoolRate);
    }
}
