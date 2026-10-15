package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Accreditation;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccreditationRepository extends JpaRepository<Accreditation, Integer> {
    Optional<Accreditation> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);

    Page<Accreditation> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
