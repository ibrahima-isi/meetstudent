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

import com.bowe.meetstudent.services.ProgramService;
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
@RequestMapping("/api/v1/program-rates")
@Tag(name = "7. Program Rates", description = "Endpoints for managing program ratings and reviews")
public class ProgramRateController {

    private final ProgramRateService service;
    private final Mapper<ProgramRate, ProgramRateDTO> mapper;
    private final ModelMapper modelMapper;
    private final ProgramService programService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Rate a program", description = "Allows students or experts to post a rating and comment for an educational program.")
    @ApiResponse(responseCode = "201", description = "Program rate created successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "400", description = "Invalid program or user ID")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<ProgramRateDTO> create(@RequestBody ProgramRateDTO dto) {
        ProgramRate rate = mapper.toEntity(dto);
        
        if (dto.getProgramId() != null) {
            var program = programService.findById(dto.getProgramId());
            if (program.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setProgram(program.get());
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
    @Operation(summary = "Get all program ratings (paginated)", description = "Retrieves a paginated list of all program ratings.")
    @ApiResponse(responseCode = "200", description = "List of program ratings retrieved")
    public Page<ProgramRateDTO> getAll(@ParameterObject Pageable pageable) {
        return service.findAll(pageable).map(mapper::toDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a program rating by ID", description = "Retrieves details of a specific program rating using its unique ID.")
    @ApiResponse(responseCode = "200", description = "Program rating found")
    @ApiResponse(responseCode = "404", description = "Program rating not found")
    public ResponseEntity<ProgramRateDTO> getById(@PathVariable Integer id) {
        return service.findById(id)
                .map(rate -> ResponseEntity.ok(mapper.toDTO(rate)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/program/{id}")
    @Operation(summary = "Get all ratings for a specific program", description = "Retrieves a list of all ratings posted for the given program ID.")
    @ApiResponse(responseCode = "200", description = "List of program ratings retrieved")
    @ApiResponse(responseCode = "404", description = "Program not found")
    public ResponseEntity<List<ProgramRateDTO>> getRatesByProgram(
            @Parameter(description = "ID of the program") @PathVariable Integer id) {
        if (!programService.exists(id)) return ResponseEntity.notFound().build();
        List<ProgramRateDTO> rates = service.findByProgramId(id)
                .stream()
                .map(mapper::toDTO)
                .toList();
        return ResponseEntity.ok(rates);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a program rating by ID", description = "Performs a full update of an existing program rating.")
    @ApiResponse(responseCode = "200", description = "Program rating updated successfully")
    @ApiResponse(responseCode = "404", description = "Program rating not found")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<ProgramRateDTO> update(@PathVariable Integer id, @RequestBody ProgramRateDTO dto) {
        Optional<ProgramRate> existing = service.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();

        ProgramRate rate = mapper.toEntity(dto);
        rate.setId(id);

        if (dto.getProgramId() != null) {
            var program = programService.findById(dto.getProgramId());
            if (program.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setProgram(program.get());
        }
        
        if (dto.getUserId() != null) {
            var user = userService.getUserById(dto.getUserId());
            if (user.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setUserEntity(user.get());
        }

        return ResponseEntity.ok(mapper.toDTO(service.save(rate)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a program rating by ID", description = "Updates only the provided fields of an existing program rating.")
    @ApiResponse(responseCode = "200", description = "Program rating patched successfully")
    @ApiResponse(responseCode = "404", description = "Program rating not found")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<ProgramRateDTO> patch(@PathVariable Integer id, @RequestBody ProgramRateDTO dto) {
        return service.findById(id).map(existing -> {
            modelMapper.map(dto, existing);
            
            if (dto.getProgramId() != null) {
                var program = programService.findById(dto.getProgramId());
                if (program.isPresent()) existing.setProgram(program.get());
            }
            
            if (dto.getUserId() != null) {
                var user = userService.getUserById(dto.getUserId());
                if (user.isPresent()) existing.setUserEntity(user.get());
            }

            return ResponseEntity.ok(mapper.toDTO(service.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a program rating by ID", description = "Removes a program rating from the system.")
    @ApiResponse(responseCode = "200", description = "Program rating deleted successfully")
    @ApiResponse(responseCode = "404", description = "Program rating not found")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<ProgramRateDTO> delete(@PathVariable Integer id) {
        return service.findById(id).map(rate -> {
            service.delete(id);
            return ResponseEntity.ok(mapper.toDTO(rate));
        }).orElse(ResponseEntity.notFound().build());
    }
}
