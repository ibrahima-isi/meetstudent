package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.SchoolRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/school-rates")
@Tag(name = "6. School Rates", description = "Endpoints for managing the school ratings and reviews")
public class SchoolRateController {

    private final SchoolRateService service;
    private final Mapper<SchoolRate, SchoolRateDTO> mapper;
    private final ModelMapper modelMapper;
    private final SchoolService schoolService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Rate a school", description = "Allows students or experts to post a rating and comment for a school.")
    @ApiResponse(responseCode = "201", description = "School rate created successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "400", description = "Invalid school or user ID")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<SchoolRateDTO> create(@RequestBody SchoolRateDTO dto) {
        SchoolRate rate = mapper.toEntity(dto);
        
        if (dto.getSchoolId() != null) {
            var school = schoolService.getSchoolById(dto.getSchoolId());
            if (school.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setSchool(school.get());
        }
        
        if (dto.getUserId() != null) {
            var user = userService.getUserById(dto.getUserId());
            if (user.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setUserEntity(user.get());
        }

        var saved = this.service.save(rate);
        return new ResponseEntity<>(mapper.toDTO(saved), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all school ratings (paginated)", description = "Retrieves a paginated list of all school ratings.")
    @ApiResponse(responseCode = "200", description = "List of school ratings retrieved")
    public Page<SchoolRateDTO> getAll(@ParameterObject Pageable pageable) {
        return service.findAll(pageable).map(mapper::toDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a school rating by ID", description = "Retrieves details of a specific school rating using its unique ID.")
    @ApiResponse(responseCode = "200", description = "School rating found")
    @ApiResponse(responseCode = "404", description = "School rating not found")
    public ResponseEntity<SchoolRateDTO> getById(@PathVariable Integer id) {
        return service.findById(id)
                .map(rate -> ResponseEntity.ok(mapper.toDTO(rate)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/school/{id}")
    @Operation(summary = "Get all ratings for a specific school", description = "Retrieves a list of all ratings posted for the given school ID.")
    @ApiResponse(responseCode = "200", description = "List of school ratings retrieved")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<List<SchoolRateDTO>> getRatesBySchool(
            @Parameter(description = "ID of the school") @PathVariable Integer id) {
        if (!schoolService.exists(id)) return ResponseEntity.notFound().build();
        List<SchoolRateDTO> rates = service.findBySchoolId(id)
                .stream()
                .map(mapper::toDTO)
                .toList();
        return ResponseEntity.ok(rates);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a school rating by ID", description = "Performs a full update of an existing school rating.")
    @ApiResponse(responseCode = "200", description = "School rating updated successfully")
    @ApiResponse(responseCode = "404", description = "School rating not found")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<SchoolRateDTO> update(@PathVariable Integer id, @RequestBody SchoolRateDTO dto) {
        Optional<SchoolRate> existing = service.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();

        SchoolRate rate = mapper.toEntity(dto);
        rate.setId(id);

        if (dto.getSchoolId() != null) {
            var school = schoolService.getSchoolById(dto.getSchoolId());
            if (school.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setSchool(school.get());
        }
        
        if (dto.getUserId() != null) {
            var user = userService.getUserById(dto.getUserId());
            if (user.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setUserEntity(user.get());
        }

        return ResponseEntity.ok(mapper.toDTO(service.save(rate)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a school rating by ID", description = "Updates only the provided fields of an existing school rating.")
    @ApiResponse(responseCode = "200", description = "School rating patched successfully")
    @ApiResponse(responseCode = "404", description = "School rating not found")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<SchoolRateDTO> patch(@PathVariable Integer id, @RequestBody SchoolRateDTO dto) {
        return service.findById(id).map(existing -> {
            modelMapper.map(dto, existing);
            
            if (dto.getSchoolId() != null) {
                var school = schoolService.getSchoolById(dto.getSchoolId());
                if (school.isPresent()) existing.setSchool(school.get());
            }
            
            if (dto.getUserId() != null) {
                var user = userService.getUserById(dto.getUserId());
                if (user.isPresent()) existing.setUserEntity(user.get());
            }

            return ResponseEntity.ok(mapper.toDTO(service.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a school rating by ID", description = "Removes a school rating from the system.")
    @ApiResponse(responseCode = "200", description = "School rating deleted successfully")
    @ApiResponse(responseCode = "404", description = "School rating not found")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<SchoolRateDTO> delete(@PathVariable Integer id) {
        return service.findById(id).map(rate -> {
            service.delete(id);
            return ResponseEntity.ok(mapper.toDTO(rate));
        }).orElse(ResponseEntity.notFound().build());
    }
}
