package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.SchoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import com.bowe.meetstudent.services.SchoolRateService;

import org.modelmapper.ModelMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/schools")
@Tag(name = "3. Schools", description = "Endpoints for managing schools")
public class SchoolController {

    private final SchoolService schoolService;
    private final SchoolMapper schoolMapper;
    private final SchoolRateService schoolRateService;
    private final ModelMapper modelMapper;

    @PostMapping
    @Operation(summary = "Create a new school")
    @ApiResponse(responseCode = "201", description = "School created successfully")
    public ResponseEntity<SchoolDTO> create(@RequestBody SchoolDTO schoolDTO) {

        School school = schoolMapper.toEntity(schoolDTO);
        var savedSchool = this.schoolService.save(school);
        SchoolDTO savedSchoolDto = schoolMapper.toDTO(savedSchool);

        return new ResponseEntity<>(savedSchoolDto, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all schools (paginated)",
        description = "Provides a paginated list of all schools.")
    @ApiResponse(responseCode = "200", description = "List of schools")
    public Page<SchoolDTO> getSchools(@ParameterObject Pageable pageable) {
        return this.schoolService
                .findAll(pageable)
                .map(school -> {
                    SchoolDTO dto = schoolMapper.toDTO(school);
                    dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(school.getId()));
                    return dto;
                });
    }

    @GetMapping(path = "/{id}")
    @Operation(summary = "Get a school by ID")
    @ApiResponse(responseCode = "302", description = "School found")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> getSchoolById(
        @Parameter(description = "ID of the school to retrieve")
        @PathVariable int id) {

        return this.schoolService.getSchoolById(id)
                .map(school -> {

                    SchoolDTO schoolDTO = schoolMapper.toDTO(school);
                    schoolDTO.setAverageRate(schoolRateService.getAverageNoteBySchoolId(school.getId()));
                    return new ResponseEntity<>(schoolDTO, HttpStatus.FOUND);

                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search schools by name (paginated)")
    @ApiResponse(responseCode = "200", description = "List of schools matching the name")
    public Page<SchoolDTO> getSchoolByName(
        @Parameter(description = "Name to search for")
        @PathVariable String name,
        @ParameterObject Pageable pageable) {

        return this.schoolService
                .getSchoolByName(name, pageable)
                .map(school -> {
                    SchoolDTO dto = schoolMapper.toDTO(school);
                    dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(school.getId()));
                    return dto;
                });
    }

    @GetMapping(path = "/address/{city}")
    @Operation(summary = "Search schools by city (paginated)")
    @ApiResponse(responseCode = "200", description = "List of schools in the specified city")
    public Page<SchoolDTO> getSchoolByCity(
        @Parameter(description = "City to search for")
        @PathVariable String city){

        Pageable pageable = PageRequest.of(0, 10, Sort.by("name").descending());
        return this.schoolService.findSchoolByCity(city, pageable)
                .map(school -> {
                    SchoolDTO dto = schoolMapper.toDTO(school);
                    dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(school.getId()));
                    return dto;
                });
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update a school by ID")
    @ApiResponse(responseCode = "200", description = "School updated successfully")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> update(
        @RequestBody SchoolDTO newSchoolDTO,
        @Parameter(description = "ID of the school to update")
        @PathVariable int id) {

        if (!this.schoolService.exists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Optional<School> existingSchool = this.schoolService.getSchoolById(id);
        School school = existingSchool.get();
        // Complete update
        School mappedUpdate = schoolMapper.toEntity(newSchoolDTO);
        mappedUpdate.setId(school.getId());

        School saved = schoolService.save(mappedUpdate);
        SchoolDTO dto = schoolMapper.toDTO(saved);
        dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(saved.getId()));
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PatchMapping(path = "/{id}")
    @Operation(summary = "Partially update a school by ID")
    @ApiResponse(responseCode = "200", description = "School patched successfully")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> patch(
        @RequestBody SchoolDTO schoolDTO,
        @Parameter(description = "ID of the school to patch")
        @PathVariable int id) {

        if (!this.schoolService.exists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Optional<School> existingSchool = this.schoolService.getSchoolById(id);
        School school = existingSchool.get();
        
        modelMapper.map(schoolDTO, school);

        School saved = schoolService.save(school);
        SchoolDTO dto = schoolMapper.toDTO(saved);
        dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(saved.getId()));
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Delete a school by ID")
    @ApiResponse(responseCode = "200", description = "School deleted successfully")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> delete(@Parameter(description = "ID of the school to delete") @PathVariable int id) {

        if (!this.schoolService.exists(id))
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        return this.schoolService.getSchoolById(id).map(toDelete -> {

            this.schoolService.delete(toDelete.getId());
            return new ResponseEntity<>(schoolMapper.toDTO(toDelete), HttpStatus.OK);
        })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
