package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.services.SchoolRateService;
import com.bowe.meetstudent.services.SchoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/schools")
@Tag(name = "3. Schools", description = "Endpoints for managing schools and universities")
public class SchoolController {

    private final SchoolService schoolService;
    private final SchoolMapper schoolMapper;
    private final SchoolRateService schoolRateService;
    private final ModelMapper modelMapper;

    @PostMapping
    @Operation(summary = "Create a new school", description = "Adds a new school/university to the platform.")
    @ApiResponse(responseCode = "201", description = "School created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input data")
    public ResponseEntity<SchoolDTO> create(@RequestBody SchoolDTO schoolDTO) {
        School school = schoolMapper.toEntity(schoolDTO);
        var savedSchool = this.schoolService.save(school);
        SchoolDTO savedSchoolDto = schoolMapper.toDTO(savedSchool);
        return new ResponseEntity<>(savedSchoolDto, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all schools (paginated)", description = "Provides a paginated list of all schools, including their average ratings. Can be sorted by rating (most/less).")
    @ApiResponse(responseCode = "200", description = "List of schools retrieved")
    public Page<SchoolDTO> getSchools(
            @Parameter(description = "Sort by rate: 'most' for highest rated, 'less' for lowest rated") 
            @RequestParam(required = false) String sortRate,
            @ParameterObject Pageable pageable) {
        
        Page<School> schoolPage;
        if ("most".equalsIgnoreCase(sortRate)) {
            schoolPage = schoolService.findAllOrderByRateDesc(pageable);
        } else if ("less".equalsIgnoreCase(sortRate)) {
            schoolPage = schoolService.findAllOrderByRateAsc(pageable);
        } else {
            schoolPage = schoolService.findAll(pageable);
        }

        return schoolPage.map(school -> {
            SchoolDTO dto = schoolMapper.toDTO(school);
            dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(school.getId()));
            return dto;
        });
    }

    @GetMapping(path = "/{id}")
    @Operation(summary = "Get a school by ID", description = "Retrieves detailed information about a school using its ID.")
    @ApiResponse(responseCode = "200", description = "School found")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> getSchoolById(
        @Parameter(description = "ID of the school to retrieve") @PathVariable int id) {
        return this.schoolService.getSchoolById(id)
                .map(school -> {
                    SchoolDTO schoolDTO = schoolMapper.toDTO(school);
                    schoolDTO.setAverageRate(schoolRateService.getAverageNoteBySchoolId(school.getId()));
                    return new ResponseEntity<>(schoolDTO, HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search schools by name (paginated)", description = "Searches for schools whose name contains the specified string.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved")
    public Page<SchoolDTO> getSchoolByName(
        @Parameter(description = "Name to search for") @PathVariable String name,
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
    @Operation(summary = "Search schools by city (paginated)", description = "Retrieves a list of schools located in the specified city.")
    @ApiResponse(responseCode = "200", description = "List of schools in the city retrieved")
    public Page<SchoolDTO> getSchoolByCity(
        @Parameter(description = "City to search for") @PathVariable String city,
        @ParameterObject Pageable pageable){
        return this.schoolService.findSchoolByCity(city, pageable)
                .map(school -> {
                    SchoolDTO dto = schoolMapper.toDTO(school);
                    dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(school.getId()));
                    return dto;
                });
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update a school by ID", description = "Performs a full update of an existing school's information.")
    @ApiResponse(responseCode = "200", description = "School updated successfully")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> update(
        @RequestBody SchoolDTO newSchoolDTO,
        @Parameter(description = "ID of the school to update") @PathVariable int id) {

        return this.schoolService.getSchoolById(id).map(school -> {
            modelMapper.map(newSchoolDTO, school);
            School saved = schoolService.save(school);
            SchoolDTO dto = schoolMapper.toDTO(saved);
            dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(saved.getId()));
            return new ResponseEntity<>(dto, HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PatchMapping(path = "/{id}")
    @Operation(summary = "Partially update a school by ID", description = "Updates only the specific fields provided for a school.")
    @ApiResponse(responseCode = "200", description = "School patched successfully")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> patch(
        @RequestBody SchoolDTO schoolDTO,
        @Parameter(description = "ID of the school to patch") @PathVariable int id) {

        return this.schoolService.getSchoolById(id).map(school -> {
            modelMapper.map(schoolDTO, school);
            School saved = schoolService.save(school);
            SchoolDTO dto = schoolMapper.toDTO(saved);
            dto.setAverageRate(schoolRateService.getAverageNoteBySchoolId(saved.getId()));
            return new ResponseEntity<>(dto, HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Delete a school by ID", description = "Removes a school from the platform.")
    @ApiResponse(responseCode = "200", description = "School deleted successfully")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<SchoolDTO> delete(
            @Parameter(description = "ID of the school to delete") @PathVariable int id) {
        return this.schoolService.getSchoolById(id).map(toDelete -> {
            this.schoolService.delete(toDelete.getId());
            return new ResponseEntity<>(schoolMapper.toDTO(toDelete), HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
