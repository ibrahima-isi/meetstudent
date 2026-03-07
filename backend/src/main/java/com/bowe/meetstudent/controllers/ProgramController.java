package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.ProgramDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.services.ProgramService;
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
@RequestMapping(path = "/api/v1/programs")
@Tag(name = "12. Programs", description = "Endpoints for managing educational programs")
public class ProgramController {

    private final ProgramService programService;
    private final SchoolService schoolService;
    private final ProgramMapper programMapper;
    private final ModelMapper modelMapper;

    @PostMapping
    @Operation(summary = "Create a new program")
    @ApiResponse(responseCode = "201", description = "Program created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid School ID")
    public ResponseEntity<ProgramDTO> create(@RequestBody ProgramDTO programDTO) {
        Program program = programMapper.toEntity(programDTO);
        
        if (programDTO.getSchoolId() != null) {
            Optional<School> schoolOpt = schoolService.getSchoolById(programDTO.getSchoolId());
            if (schoolOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            program.setSchool(schoolOpt.get());
        } else {
            program.setSchool(null);
        }

        Program savedProgram = programService.save(program);
        return new ResponseEntity<>(programMapper.toDTO(savedProgram), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all programs (paginated)", description = "Provides a paginated list of all programs.")
    @ApiResponse(responseCode = "200", description = "List of programs")
    public Page<ProgramDTO> getPrograms(@ParameterObject Pageable pageable) {
        return programService.findAll(pageable).map(programMapper::toDTO);
    }

    @GetMapping(path = "/{id}")
    @Operation(summary = "Get a program by ID")
    @ApiResponse(responseCode = "302", description = "Program found")
    @ApiResponse(responseCode = "404", description = "Program not found")
    public ResponseEntity<ProgramDTO> getProgramById(
            @Parameter(description = "ID of the program to retrieve") @PathVariable int id) {
        return programService.findById(id)
                .map(program -> new ResponseEntity<>(programMapper.toDTO(program), HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search programs by name (paginated)")
    @ApiResponse(responseCode = "200", description = "List of programs matching the name")
    public Page<ProgramDTO> getProgramByName(
            @Parameter(description = "Name to search for") @PathVariable String name,
            @ParameterObject Pageable pageable) {
        return programService.findByName(name, pageable).map(programMapper::toDTO);
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update a program by ID")
    @ApiResponse(responseCode = "200", description = "Program updated successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @ApiResponse(responseCode = "400", description = "Invalid School ID")
    public ResponseEntity<ProgramDTO> update(
            @RequestBody ProgramDTO newProgramDTO,
            @Parameter(description = "ID of the program to update") @PathVariable int id) {
        
        Optional<Program> existingOpt = programService.findById(id);
        if (existingOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Program existingProgram = existingOpt.get();
        // Complete update so map everything
        Program mappedUpdate = programMapper.toEntity(newProgramDTO);
        mappedUpdate.setId(existingProgram.getId());
        
        if (newProgramDTO.getSchoolId() != null) {
            Optional<School> schoolOpt = schoolService.getSchoolById(newProgramDTO.getSchoolId());
            if (schoolOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            mappedUpdate.setSchool(schoolOpt.get());
        }

        return new ResponseEntity<>(programMapper.toDTO(programService.save(mappedUpdate)), HttpStatus.OK);
    }

    @PatchMapping(path = "/{id}")
    @Operation(summary = "Partially update a program by ID")
    @ApiResponse(responseCode = "200", description = "Program patched successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @ApiResponse(responseCode = "400", description = "Invalid School ID")
    public ResponseEntity<ProgramDTO> patch(
            @RequestBody ProgramDTO programDTO,
            @Parameter(description = "ID of the program to patch") @PathVariable int id) {
        
        Optional<Program> existingOpt = programService.findById(id);
        if (existingOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Program existingProgram = existingOpt.get();
        
        // Use ModelMapper to update non-null fields
        modelMapper.map(programDTO, existingProgram);
        
        if (programDTO.getSchoolId() != null) {
            Optional<School> schoolOpt = schoolService.getSchoolById(programDTO.getSchoolId());
            if (schoolOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            existingProgram.setSchool(schoolOpt.get());
        }

        return new ResponseEntity<>(programMapper.toDTO(programService.save(existingProgram)), HttpStatus.OK);
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Delete a program by ID")
    @ApiResponse(responseCode = "200", description = "Program deleted successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    public ResponseEntity<ProgramDTO> delete(@Parameter(description = "ID of the program to delete") @PathVariable int id) {
        return programService.findById(id).map(toDelete -> {
            programService.delete(toDelete.getId());
            return new ResponseEntity<>(programMapper.toDTO(toDelete), HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
