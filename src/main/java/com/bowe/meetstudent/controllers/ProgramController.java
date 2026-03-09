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

import com.bowe.meetstudent.dto.ProgramAccreditationDTO;
import com.bowe.meetstudent.entities.ProgramAccreditation;
import com.bowe.meetstudent.mappers.implementations.ProgramAccreditationMapper;
import com.bowe.meetstudent.services.ProgramAccreditationService;
import com.bowe.meetstudent.services.ProgramRateService;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/programs")
@Tag(name = "12. Programs", description = "Endpoints for managing educational programs (Degrees, Certifications, etc.)")
public class ProgramController {

    private final ProgramService programService;
    private final SchoolService schoolService;
    private final ProgramMapper programMapper;
    private final ModelMapper modelMapper;
    private final ProgramAccreditationService programAccreditationService;
    private final ProgramAccreditationMapper programAccreditationMapper;
    private final ProgramRateService programRateService;

    @PostMapping(path = "/{programId}/accreditations/{accreditationId}")
    @Operation(summary = "Link an accreditation to a program", description = "Associates a specific accreditation with a program, specifying start and end years.")
    @ApiResponse(responseCode = "201", description = "Accreditation linked successfully")
    @ApiResponse(responseCode = "404", description = "Program or Accreditation not found")
    public ResponseEntity<ProgramAccreditationDTO> linkAccreditation(
            @Parameter(description = "ID of the program") @PathVariable Integer programId,
            @Parameter(description = "ID of the accreditation") @PathVariable Integer accreditationId,
            @Parameter(description = "Year the accreditation starts") @RequestParam Integer startsAt,
            @Parameter(description = "Year the accreditation expires") @RequestParam Integer endsAt) {
        ProgramAccreditation linked = programAccreditationService.addAccreditationToProgram(programId, accreditationId, startsAt, endsAt);
        return new ResponseEntity<>(programAccreditationMapper.toDTO(linked), HttpStatus.CREATED);
    }

    @GetMapping(path = "/{programId}/accreditations/{accreditationId}")
    @Operation(summary = "Get a specific accreditation for a program", description = "Retrieves details of a specific accreditation linked to a program.")
    @ApiResponse(responseCode = "200", description = "Program accreditation found")
    @ApiResponse(responseCode = "404", description = "Association not found")
    public ResponseEntity<ProgramAccreditationDTO> getProgramAccreditation(
            @Parameter(description = "ID of the program") @PathVariable Integer programId,
            @Parameter(description = "ID of the accreditation") @PathVariable Integer accreditationId) {
        return programAccreditationService.findById(programId, accreditationId)
                .map(pa -> ResponseEntity.ok(programAccreditationMapper.toDTO(pa)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/{programId}/accreditations/{accreditationId}")
    @Operation(summary = "Update an accreditation for a program", description = "Updates the start and end years of a specific accreditation linked to a program.")
    @ApiResponse(responseCode = "200", description = "Accreditation updated successfully")
    @ApiResponse(responseCode = "404", description = "Association not found")
    public ResponseEntity<ProgramAccreditationDTO> updateAccreditation(
            @Parameter(description = "ID of the program") @PathVariable Integer programId,
            @Parameter(description = "ID of the accreditation") @PathVariable Integer accreditationId,
            @Parameter(description = "Year the accreditation starts") @RequestParam Integer startsAt,
            @Parameter(description = "Year the accreditation expires") @RequestParam Integer endsAt) {
        ProgramAccreditation updated = programAccreditationService.updateAccreditationForProgram(programId, accreditationId, startsAt, endsAt);
        return ResponseEntity.ok(programAccreditationMapper.toDTO(updated));
    }

    @DeleteMapping(path = "/{programId}/accreditations/{accreditationId}")
    @Operation(summary = "Unlink an accreditation from a program", description = "Removes the association between a program and an accreditation.")
    @ApiResponse(responseCode = "204", description = "Accreditation unlinked successfully")
    @ApiResponse(responseCode = "404", description = "Association not found")
    public ResponseEntity<Void> unlinkAccreditation(
            @Parameter(description = "ID of the program") @PathVariable Integer programId,
            @Parameter(description = "ID of the accreditation") @PathVariable Integer accreditationId) {
        programAccreditationService.removeAccreditationFromProgram(programId, accreditationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/{programId}/accreditations")
    @Operation(summary = "Get all accreditations for a program", description = "Retrieves a list of all accreditations currently held by the specified program.")
    @ApiResponse(responseCode = "200", description = "List of program accreditations retrieved")
    public ResponseEntity<List<ProgramAccreditationDTO>> getProgramAccreditations(
            @Parameter(description = "ID of the program") @PathVariable Integer programId) {
        List<ProgramAccreditationDTO> list = programAccreditationService.findByProgramId(programId)
                .stream()
                .map(programAccreditationMapper::toDTO)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping
    @Operation(summary = "Create a new program", description = "Adds a new educational program to the system, optionally linking it to a school.")
    @ApiResponse(responseCode = "201", description = "Program created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid School ID or input data")
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
    @Operation(summary = "Get all programs (paginated)", description = "Provides a paginated list of all programs, including their average ratings. Can be sorted by rating (most/less).")
    @ApiResponse(responseCode = "200", description = "List of programs retrieved")
    public Page<ProgramDTO> getPrograms(
            @Parameter(description = "Sort by rate: 'most' for highest rated, 'less' for lowest rated") 
            @RequestParam(required = false) String sortRate,
            @ParameterObject Pageable pageable) {
        
        Page<Program> programPage;
        if ("most".equalsIgnoreCase(sortRate)) {
            programPage = programService.findAllOrderByRateDesc(pageable);
        } else if ("less".equalsIgnoreCase(sortRate)) {
            programPage = programService.findAllOrderByRateAsc(pageable);
        } else {
            programPage = programService.findAll(pageable);
        }

        return programPage.map(program -> {
            ProgramDTO dto = programMapper.toDTO(program);
            dto.setAverageRate(programRateService.getAverageNoteByProgramId(program.getId()));
            return dto;
        });
    }

    @GetMapping(path = "/{id}")
    @Operation(summary = "Get a program by ID", description = "Retrieves details of a specific program using its unique ID.")
    @ApiResponse(responseCode = "302", description = "Program found")
    @ApiResponse(responseCode = "404", description = "Program not found")
    public ResponseEntity<ProgramDTO> getProgramById(
            @Parameter(description = "ID of the program to retrieve") @PathVariable int id) {
        return programService.findById(id)
                .map(program -> {
                    ProgramDTO dto = programMapper.toDTO(program);
                    dto.setAverageRate(programRateService.getAverageNoteByProgramId(program.getId()));
                    return new ResponseEntity<>(dto, HttpStatus.FOUND);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search programs by name (paginated)", description = "Searches for programs whose name contains the given string.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved")
    public Page<ProgramDTO> getProgramByName(
            @Parameter(description = "Name to search for") @PathVariable String name,
            @ParameterObject Pageable pageable) {
        return programService.findByName(name, pageable).map(program -> {
            ProgramDTO dto = programMapper.toDTO(program);
            dto.setAverageRate(programRateService.getAverageNoteByProgramId(program.getId()));
            return dto;
        });
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update a program by ID", description = "Performs a full update of an existing program.")
    @ApiResponse(responseCode = "200", description = "Program updated successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @ApiResponse(responseCode = "400", description = "Invalid School ID")
    public ResponseEntity<ProgramDTO> update(
            @RequestBody ProgramDTO newProgramDTO,
            @Parameter(description = "ID of the program to update") @PathVariable int id) {
        
        Program updates = programMapper.toEntity(newProgramDTO);
        if (newProgramDTO.getSchoolId() != null) {
            Optional<School> schoolOpt = schoolService.getSchoolById(newProgramDTO.getSchoolId());
            if (schoolOpt.isEmpty()) return ResponseEntity.badRequest().build();
            updates.setSchool(schoolOpt.get());
        }

        Program saved = programService.patch(id, updates);
        return ResponseEntity.ok(programMapper.toDTO(saved));
    }

    @PatchMapping(path = "/{id}")
    @Operation(summary = "Partially update a program by ID", description = "Updates only the provided fields of an existing program.")
    @ApiResponse(responseCode = "200", description = "Program patched successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @ApiResponse(responseCode = "400", description = "Invalid School ID")
    public ResponseEntity<ProgramDTO> patch(
            @RequestBody ProgramDTO programDTO,
            @Parameter(description = "ID of the program to patch") @PathVariable int id) {
        
        Program updates = programMapper.toEntity(programDTO);
        if (programDTO.getSchoolId() != null) {
            Optional<School> schoolOpt = schoolService.getSchoolById(programDTO.getSchoolId());
            if (schoolOpt.isPresent()) updates.setSchool(schoolOpt.get());
        }

        Program saved = programService.patch(id, updates);
        return ResponseEntity.ok(programMapper.toDTO(saved));
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Delete a program by ID", description = "Removes a program from the system.")
    @ApiResponse(responseCode = "200", description = "Program deleted successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    public ResponseEntity<ProgramDTO> delete(@Parameter(description = "ID of the program to delete") @PathVariable int id) {
        return programService.findById(id).map(toDelete -> {
            programService.delete(toDelete.getId());
            return new ResponseEntity<>(programMapper.toDTO(toDelete), HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
