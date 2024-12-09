package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/schools")
public class SchoolController {

    private final SchoolService schoolService;
    private final SchoolMapper schoolMapper;

    @PostMapping
    private ResponseEntity<SchoolDTO> create(@RequestBody SchoolDTO schoolDTO) {

        School school = schoolMapper.toEntity(schoolDTO);
        var savedSchool = this.schoolService.create(school);
        SchoolDTO savedSchoolDto = schoolMapper.toDTO(savedSchool);

        return new ResponseEntity<>(savedSchoolDto, HttpStatus.CREATED);
    }

    @GetMapping
    public List<SchoolDTO> getSchools() {

        return this.schoolService
                .getSchools()
                .stream()
                .map(schoolMapper::toDTO)
                .toList();
    }


    @GetMapping(path = "/{id}")
    public ResponseEntity<SchoolDTO> getSchoolById(@PathVariable int id) {

        return this.schoolService.getSchoolById(id)
                .map(school -> {

                    SchoolDTO schoolDTO = schoolMapper.toDTO(school);
                    return new ResponseEntity<>(schoolDTO, HttpStatus.FOUND);

                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    public List<School> getSchoolById(@PathVariable String name) {

        return this.schoolService.getSchoolByName(name);
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<School> update(@RequestBody School newSchool, @PathVariable int id) {

        if (!schoolService.exists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        newSchool.setId(id);
        School updatedSchool = this.schoolService.update(newSchool);
        return new ResponseEntity<>(updatedSchool, HttpStatus.OK);
    }

    @PatchMapping(path = "/{id}")
    public ResponseEntity<School> patch(@RequestBody School newSchool, @PathVariable int id) {

        if (!schoolService.exists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return this.schoolService.getSchoolById(id).map(existingSchool -> {
            if (newSchool.getAddress() != null) {
                existingSchool.setAddress(newSchool.getAddress());
            }
            if (newSchool.getName() != null) {
                existingSchool.setName(newSchool.getName());
            }
            if (newSchool.getCreation() != null) {
                existingSchool.setCreation(newSchool.getCreation());
            }
            School updatedSchool = this.schoolService.patch(existingSchool);
            return new ResponseEntity<>(updatedSchool, HttpStatus.OK);

        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));

    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<School> delete(@PathVariable int id) {

        if (!this.schoolService.exists(id))
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        return this.schoolService.getSchoolById(id).map(deletedSchool -> {

            this.schoolService.delete(deletedSchool.getId());
            return new ResponseEntity<>(deletedSchool, HttpStatus.OK);

        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

}
