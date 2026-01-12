package com.bowe.meetstudent.services;

import com.bowe.meetstudent.repositories.SchoolRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchoolRateService {
    private final SchoolRateRepository schoolRateRepository;
}
