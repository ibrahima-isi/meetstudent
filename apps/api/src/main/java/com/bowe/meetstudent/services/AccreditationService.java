package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.exceptions.ConflictException;
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
        if (accreditation.getCode() != null && (accreditation.getId() == null
                ? accreditationRepository.existsByCodeIgnoreCase(accreditation.getCode())
                : accreditationRepository.existsByCodeIgnoreCaseAndIdNot(accreditation.getCode(), accreditation.getId()))) {
            throw new ConflictException("An accreditation with this code already exists");
        }
        return this.accreditationRepository.save(accreditation);
    }

    public Page<Accreditation> findAll(Pageable pageable) {
        return this.accreditationRepository.findAll(pageable);
    }

    public Optional<Accreditation> findById(int id) {
        return this.accreditationRepository.findById(id);
    }

    public Page<Accreditation> findByName(String name, Pageable pageable) {
        String safeName = (name == null) ? "" : name;
        return this.accreditationRepository.findByNameContainingIgnoreCase(safeName, pageable);
    }

    public boolean exists(int id) {
        return this.accreditationRepository.existsById(id);
    }

    @Transactional
    public void delete(int id) {
        this.accreditationRepository.findById(id).ifPresent(accreditation -> {
            if (accreditation.getProgramAccreditations() != null
                    && !accreditation.getProgramAccreditations().isEmpty()) {
                throw new ConflictException("This accreditation is still linked to programs");
            }
            this.accreditationRepository.delete(accreditation);
        });
    }
}
