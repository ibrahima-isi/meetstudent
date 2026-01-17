package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.CourseRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/course-rates")
@Tag(name = "Course Rate", description = "Endpoint for managing course ratings")
public class CourseRateController {
    private final CourseRateService service;
    private final Mapper<CourseRate, CourseRateDTO> mapper;

    @PostMapping
    @Operation(summary = "Rate a course")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<CourseRateDTO> create(@RequestBody CourseRateDTO dto) {
        CourseRate rate = mapper.toEntity(dto);
        var saved = this.service.save(rate);
        return new ResponseEntity<>(mapper.toDTO(saved), HttpStatus.CREATED);
    }
}
