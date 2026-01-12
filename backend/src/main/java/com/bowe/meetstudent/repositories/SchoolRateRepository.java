package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.rates.SchoolRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchoolRateRepository extends JpaRepository<SchoolRate, Integer> {
}
