package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.ProgramRateService;
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
@RequestMapping("/api/v1/program-rates")
@Tag(name = "Program Rate", description = "Endpoint for managing program ratings")
public class ProgramRateController {

    private final ProgramRateService service;
    private final Mapper<ProgramRate, ProgramRateDTO> mapper;

    @PostMapping
    @Operation(summary = "Rate a program")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<ProgramRateDTO> create(@RequestBody ProgramRateDTO dto) {
        ProgramRate rate = mapper.toEntity(dto);
        var saved = this.service.save(rate);
        return new ResponseEntity<>(mapper.toDTO(saved), HttpStatus.CREATED);
    }
}