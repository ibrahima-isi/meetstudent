package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.mappers.implementations.CourseRateMapper;
import com.bowe.meetstudent.services.CourseRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/rates/courses")
@Tag(name = "7. Course Rates", description = "Endpoints for managing course ratings")
public class CourseRateController {

    private final CourseRateService courseRateService;
    private final CourseRateMapper courseRateMapper;

    @PostMapping
    @Operation(summary = "Rate a course")
    public ResponseEntity<CourseRateDTO> create(@RequestBody CourseRateDTO courseRateDTO) {
        return null;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get rate details by ID")
    public ResponseEntity<CourseRateDTO> getById(@Parameter(description = "ID of the rate") @PathVariable Integer id) {
        return null;
    }
}
