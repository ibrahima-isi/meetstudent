package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.repositories.AccreditationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccreditationService {

    private final AccreditationRepository accreditationRepository;

    @Transactional
    public Accreditation save(Accreditation accreditation) {
        return this.accreditationRepository.save(accreditation);
    }

    public Page<Accreditation> findAll(Pageable pageable) {
        return this.accreditationRepository.findAll(pageable);
    }

    public Optional<Accreditation> findById(int id) {
        return this.accreditationRepository.findById(id);
    }

    public Page<Accreditation> findByName(String name, Pageable pageable) {
        return this.accreditationRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public boolean exists(int id) {
        return this.accreditationRepository.existsById(id);
    }

    @Transactional
    public void delete(int id) {
        this.accreditationRepository.deleteById(id);
    }
}
