package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.rates.ProgramRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRateRepository extends JpaRepository<ProgramRate, Integer> {
}
