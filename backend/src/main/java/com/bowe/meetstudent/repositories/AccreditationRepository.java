package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Accreditation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccreditationRepository extends JpaRepository<Accreditation, Integer> {
}
