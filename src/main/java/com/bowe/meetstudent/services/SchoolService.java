package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.repositories.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final MediaService mediaService;


    public School save(School school) {
        return this.schoolRepository.save(school);
    }

    public List<School> getSchools() {
        return this.schoolRepository.findAll();
    }

    public Page<School> findAll(Pageable pageable){
        return this.schoolRepository.findAll(pageable);
    }

    public Optional<School> getSchoolById(int id){
        return this.schoolRepository.findById(id);
    }

    @Transactional
    public void delete(int id){
        this.schoolRepository.findById(id).ifPresent(school -> {
            mediaService.deleteMediaByUrl(school.getLogoUrl());
            mediaService.deleteMediaByUrl(school.getCoverPhotoUrl());
            this.schoolRepository.deleteById(id);
        });
    }

    public Boolean exists(int id){
        return this.schoolRepository.existsById(id);
    }

    public Page<School> getSchoolByName(String name, Pageable pageable) {
        return this.schoolRepository.findSchoolByNameContainingIgnoreCase(name, pageable);
    }

    public Page<School> findSchoolByCity(String city, Pageable pageable) {
        return this.schoolRepository.findSchoolByAddress_City(city, pageable);
    }

    public Page<School> findSchoolByCountry(String country, Pageable pageable) {
        return this.schoolRepository.findSchoolByAddress_Country(country, pageable);
    }

    public Page<School> searchSchools(String city, String country, String programName, Pageable pageable) {
        return this.schoolRepository.searchSchools(city, country, programName, pageable);
    }

    public Page<School> findAllOrderByRateDesc(Pageable pageable) {
        return schoolRepository.findAllByOrderByAverageRateDesc(pageable);
    }

    public Page<School> findAllOrderByRateAsc(Pageable pageable) {
        return schoolRepository.findAllByOrderByAverageRateAsc(pageable);
    }

    @Transactional
    public School patch(Integer id, School updates) {
        return schoolRepository.findById(id).map(existing -> {
            // Check for media changes before updating
            mediaService.deleteOldMediaIfChanged(existing.getLogoUrl(), updates.getLogoUrl());
            mediaService.deleteOldMediaIfChanged(existing.getCoverPhotoUrl(), updates.getCoverPhotoUrl());
            
            // Map remaining fields
            if (updates.getName() != null) existing.setName(updates.getName());
            if (updates.getCode() != null) existing.setCode(updates.getCode());
            if (updates.getCreation() != null) existing.setCreation(updates.getCreation());
            if (updates.getAddress() != null) existing.setAddress(updates.getAddress());
            if (updates.getLogoUrl() != null) existing.setLogoUrl(updates.getLogoUrl());
            if (updates.getCoverPhotoUrl() != null) existing.setCoverPhotoUrl(updates.getCoverPhotoUrl());
            
            return schoolRepository.save(existing);
        }).orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("School not found"));
    }
}
