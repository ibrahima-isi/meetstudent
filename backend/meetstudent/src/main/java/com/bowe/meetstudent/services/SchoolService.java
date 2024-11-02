package com.bowe.meetstudent.services;

import aj.org.objectweb.asm.commons.Remapper;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.repositories.SchoolRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }


    public School create(School school) {

        return this.schoolRepository.save(school);
    }

    public List<School> getSchools() {

        return this.schoolRepository.findAll();
    }

    public Optional<School> getSchoolById(int id){
        return this.schoolRepository.findById(id);
    }

    public School update(School school){
        return this.schoolRepository.save(school);
    }

    public School patch(School school){

        return this.schoolRepository.save(school);
    }

    public void delete(int id){

        this.schoolRepository.deleteById(id);
    }

    public Boolean exists(int id){

        return this.schoolRepository.existsById(id);
    }

    public List<School> getSchoolByName(String name) {
        return this.schoolRepository.findSchoolByName(name);
    }
}
