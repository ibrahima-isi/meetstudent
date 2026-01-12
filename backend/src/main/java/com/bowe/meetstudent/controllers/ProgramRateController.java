package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.mappers.implementations.ProgramRateMapper;
import com.bowe.meetstudent.services.ProgramRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/rates/programs")
@Tag(name = "6. Program Rates", description = "Endpoints for managing program ratings")
public class ProgramRateController {

    private final ProgramRateService programRateService;
    private final ProgramRateMapper programRateMapper;

    @PostMapping
    @Operation(summary = "Rate a program")
    public ResponseEntity<ProgramRateDTO> create(@RequestBody ProgramRateDTO programRateDTO) {
        return null;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get rate details by ID")
    public ResponseEntity<ProgramRateDTO> getById(@Parameter(description = "ID of the rate") @PathVariable Integer id) {
        return null;
    }
}
