package com.bowe.meetstudent.services;

import com.bowe.meetstudent.repositories.ProgramRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProgramRateService {
    private final ProgramRateRepository programRateRepository;
}
