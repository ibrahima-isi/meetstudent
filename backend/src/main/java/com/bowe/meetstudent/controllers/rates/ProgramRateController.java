package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.ProgramRateService;
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
@RequestMapping("/api/v1/program-rates")
@Tag(name = "7. Program Rates", description = "Endpoints for managing program ratings and reviews")
public class ProgramRateController {

    private final ProgramRateService service;
    private final Mapper<ProgramRate, ProgramRateDTO> mapper;

    @PostMapping
    @Operation(summary = "Rate a program", description = "Allows students or experts to post a rating and comment for an educational program.")
    @ApiResponse(responseCode = "201", description = "Program rate created successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<ProgramRateDTO> create(@RequestBody ProgramRateDTO dto) {
        ProgramRate rate = mapper.toEntity(dto);
        var saved = this.service.save(rate);
        return new ResponseEntity<>(mapper.toDTO(saved), HttpStatus.CREATED);
    }

    @GetMapping("/program/{id}")
    @Operation(summary = "Get all ratings for a specific program", description = "Retrieves a list of all ratings posted for the given program ID.")
    @ApiResponse(responseCode = "200", description = "List of program ratings retrieved")
    @ApiResponse(responseCode = "404", description = "Program not found")
    public ResponseEntity<List<ProgramRateDTO>> getRatesByProgram(
            @Parameter(description = "ID of the program") @PathVariable Integer id) {
        List<ProgramRateDTO> rates = service.findByProgramId(id)
                .stream()
                .map(mapper::toDTO)
                .toList();
        return ResponseEntity.ok(rates);
    }
}
