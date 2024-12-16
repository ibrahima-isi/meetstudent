package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.repositories.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolRepository schoolRepository;


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

    public void delete(int id){
        this.schoolRepository.deleteById(id);
    }

    public Boolean exists(int id){
        return this.schoolRepository.existsById(id);
    }

    public Page<School> getSchoolByName(String name, Pageable pageable) {
        return this.schoolRepository.findSchoolByName(name, pageable);
    }

    public Page<School> findSchoolByCity(String city, Pageable pageable) {
        return this.schoolRepository.findSchoolByAddress_City(city, pageable);
    }
}
