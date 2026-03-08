package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.AccreditationDTO;
import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.mappers.implementations.AccreditationMapper;
import com.bowe.meetstudent.services.AccreditationService;
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
@RequestMapping(path = "/api/v1/accreditations")
@Tag(name = "8. Accreditations", description = "Endpoints for managing accreditations")
public class AccreditationController {

    private final AccreditationService accreditationService;
    private final AccreditationMapper accreditationMapper;
    private final ModelMapper modelMapper;

    @PostMapping
    @Operation(summary = "Create a new accreditation")
    @ApiResponse(responseCode = "201", description = "Accreditation created successfully")
    public ResponseEntity<AccreditationDTO> create(@RequestBody AccreditationDTO accreditationDTO) {
        Accreditation accreditation = accreditationMapper.toEntity(accreditationDTO);
        Accreditation saved = accreditationService.save(accreditation);
        return new ResponseEntity<>(accreditationMapper.toDTO(saved), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all accreditations (paginated)")
    @ApiResponse(responseCode = "200", description = "List of accreditations")
    public Page<AccreditationDTO> getAccreditations(@ParameterObject Pageable pageable) {
        return accreditationService.findAll(pageable).map(accreditationMapper::toDTO);
    }

    @GetMapping(path = "/{id}")
    @Operation(summary = "Get an accreditation by ID")
    @ApiResponse(responseCode = "302", description = "Accreditation found")
    @ApiResponse(responseCode = "404", description = "Accreditation not found")
    public ResponseEntity<AccreditationDTO> getAccreditationById(
            @Parameter(description = "ID of the accreditation to retrieve") @PathVariable int id) {
        return accreditationService.findById(id)
                .map(acc -> new ResponseEntity<>(accreditationMapper.toDTO(acc), HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search accreditations by name (paginated)")
    @ApiResponse(responseCode = "200", description = "List of accreditations matching the name")
    public Page<AccreditationDTO> getAccreditationByName(
            @Parameter(description = "Name to search for") @PathVariable String name,
            @ParameterObject Pageable pageable) {
        return accreditationService.findByName(name, pageable).map(accreditationMapper::toDTO);
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update an accreditation by ID")
    @ApiResponse(responseCode = "200", description = "Accreditation updated successfully")
    @ApiResponse(responseCode = "404", description = "Accreditation not found")
    public ResponseEntity<AccreditationDTO> update(
            @RequestBody AccreditationDTO newAccDTO,
            @Parameter(description = "ID of the accreditation to update") @PathVariable int id) {
        
        Optional<Accreditation> existingOpt = accreditationService.findById(id);
        if (existingOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Accreditation mappedUpdate = accreditationMapper.toEntity(newAccDTO);
        mappedUpdate.setId(id);

        return new ResponseEntity<>(accreditationMapper.toDTO(accreditationService.save(mappedUpdate)), HttpStatus.OK);
    }

    @PatchMapping(path = "/{id}")
    @Operation(summary = "Partially update an accreditation by ID")
    @ApiResponse(responseCode = "200", description = "Accreditation patched successfully")
    @ApiResponse(responseCode = "404", description = "Accreditation not found")
    public ResponseEntity<AccreditationDTO> patch(
            @RequestBody AccreditationDTO accDTO,
            @Parameter(description = "ID of the accreditation to patch") @PathVariable int id) {
        
        Optional<Accreditation> existingOpt = accreditationService.findById(id);
        if (existingOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Accreditation existingAcc = existingOpt.get();
        modelMapper.map(accDTO, existingAcc);

        return new ResponseEntity<>(accreditationMapper.toDTO(accreditationService.save(existingAcc)), HttpStatus.OK);
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Delete an accreditation by ID")
    @ApiResponse(responseCode = "200", description = "Accreditation deleted successfully")
    @ApiResponse(responseCode = "404", description = "Accreditation not found")
    public ResponseEntity<AccreditationDTO> delete(@Parameter(description = "ID of the accreditation to delete") @PathVariable int id) {
        return accreditationService.findById(id).map(toDelete -> {
            accreditationService.delete(toDelete.getId());
            return new ResponseEntity<>(accreditationMapper.toDTO(toDelete), HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
