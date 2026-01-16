package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.SchoolRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/school-rates")
@Tag(name = "School Rates", description = "Endpoints for managing the school rates")
public class SchoolRateController {

    private final SchoolRateService service;
    private final Mapper<SchoolRate, SchoolRateDTO> mapper;


    @PostMapping
    @Operation(summary = "Rate a school")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<SchoolRateDTO> create(@RequestBody SchoolRateDTO dto) {
        SchoolRate rate = mapper.toEntity(dto);
        var saved = this.service.save(rate);
        return new ResponseEntity<>(mapper.toDTO(saved), HttpStatus.CREATED);
    }

}
