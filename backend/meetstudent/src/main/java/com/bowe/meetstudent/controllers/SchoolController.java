package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/schools")
public class SchoolController {

    private final SchoolService schoolService;
    private final SchoolMapper schoolMapper;

    @PostMapping
    private ResponseEntity<SchoolDTO> create(@RequestBody SchoolDTO schoolDTO) {

        School school = schoolMapper.toEntity(schoolDTO);
        var savedSchool = this.schoolService.save(school);
        SchoolDTO savedSchoolDto = schoolMapper.toDTO(savedSchool);

        return new ResponseEntity<>(savedSchoolDto, HttpStatus.CREATED);
    }

    @GetMapping
    public Page<SchoolDTO> getSchools(Pageable pageable) {
        return this.schoolService
                .findAll(pageable)
                .map(schoolMapper::toDTO);
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
    public Page<SchoolDTO> getSchoolByName(@PathVariable String name, Pageable pageable) {

        return this.schoolService
                .getSchoolByName(name, pageable)
                .map(schoolMapper::toDTO);
    }

    @GetMapping(path = "/address/{city}")
    public Page<SchoolDTO> getSchoolByCity(@PathVariable String city){

        Pageable pageable = PageRequest.of(0, 10, Sort.by("name").descending()); // 10 schools per page, sorted by name ascending
        return this.schoolService.findSchoolByCity(city, pageable)
                .map(schoolMapper::toDTO);
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<SchoolDTO> update(@RequestBody SchoolDTO newSchool, @PathVariable int id) {

        return getSchoolDTOResponseEntity(newSchool, id);
    }

    @PatchMapping(path = "/{id}")
    public ResponseEntity<SchoolDTO> patch(@RequestBody SchoolDTO schoolDTO, @PathVariable int id) {

        return getSchoolDTOResponseEntity(schoolDTO, id);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<SchoolDTO> delete(@PathVariable int id) {

        if (!this.schoolService.exists(id))
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        return this.schoolService.getSchoolById(id).map(toDelete -> {

            this.schoolService.delete(toDelete.getId());
            return new ResponseEntity<>(schoolMapper.toDTO(toDelete), HttpStatus.OK);
        })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private School setSchoolValuesForUpdate(SchoolDTO schoolDTO, Optional<School> existingSchool) {

        School school = existingSchool.orElse(new School());

        if (schoolDTO.getAddress() != null) {
            school.setAddress(schoolDTO.getAddress());
        }
        if (schoolDTO.getName() != null) {
            school.setName(schoolDTO.getName());
        }
        if (schoolDTO.getCreation() != null) {
            school.setCreation(schoolDTO.getCreation());
        }
        if (schoolDTO.getCode() != null){
            school.setCode(schoolDTO.getCode());
        }
        System.out.println("Set values : "+school);
        return school;
    }

    private ResponseEntity<SchoolDTO> getSchoolDTOResponseEntity(SchoolDTO newSchool, int id) {

        if (!this.schoolService.exists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Optional<School> existingSchool = this.schoolService.getSchoolById(id);
        School toSave =  setSchoolValuesForUpdate(newSchool, existingSchool);
        System.out.println("To save : "+ toSave);

        return new ResponseEntity<>(
                this.schoolMapper.toDTO(
                        this.schoolService.save(toSave)
                ),
                HttpStatus.OK
        );
    }
}
