package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.mappers.implementations.SchoolRateMapper;
import com.bowe.meetstudent.services.SchoolRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/rates/schools")
@Tag(name = "5. School Rates", description = "Endpoints for managing school ratings")
public class SchoolRateController {

    private final SchoolRateService schoolRateService;
    private final SchoolRateMapper schoolRateMapper;

    @PostMapping
    @Operation(summary = "Rate a school")
    public ResponseEntity<SchoolRateDTO> create(@RequestBody SchoolRateDTO schoolRateDTO) {
        return null;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get rate details by ID")
    public ResponseEntity<SchoolRateDTO> getById(@Parameter(description = "ID of the rate") @PathVariable Integer id) {
        return null;
    }
}
