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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/school-rates")
@Tag(name = "6. School Rates", description = "Endpoints for managing the school ratings and reviews")
public class SchoolRateController {

    private final SchoolRateService service;
    private final Mapper<SchoolRate, SchoolRateDTO> mapper;

    @PostMapping
    @Operation(summary = "Rate a school", description = "Allows students or experts to post a rating and comment for a school.")
    @ApiResponse(responseCode = "201", description = "School rate created successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<SchoolRateDTO> create(@RequestBody SchoolRateDTO dto) {
        SchoolRate rate = mapper.toEntity(dto);
        var saved = this.service.save(rate);
        return new ResponseEntity<>(mapper.toDTO(saved), HttpStatus.CREATED);
    }

    @GetMapping("/school/{id}")
    @Operation(summary = "Get all ratings for a specific school", description = "Retrieves a list of all ratings posted for the given school ID.")
    @ApiResponse(responseCode = "200", description = "List of school ratings retrieved")
    @ApiResponse(responseCode = "404", description = "School not found")
    public ResponseEntity<List<SchoolRateDTO>> getRatesBySchool(
            @Parameter(description = "ID of the school") @PathVariable Integer id) {
        List<SchoolRateDTO> rates = service.findBySchoolId(id)
                .stream()
                .map(mapper::toDTO)
                .toList();
        return ResponseEntity.ok(rates);
    }
}
