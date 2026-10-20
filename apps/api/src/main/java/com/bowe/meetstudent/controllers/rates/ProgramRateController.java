package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.security.RequiresApprovedExpert;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.ProgramRateService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/program-rates")
@Tag(name = "10. Program Rates", description = "Endpoints for managing program ratings and reviews")
public class ProgramRateController {

    private final ProgramRateService service;
    private final Mapper<ProgramRate, ProgramRateDTO> mapper;
    private final ProgramService programService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Rate a program", description = "Allows experts to post a rating and comment for an educational program.")
    @RequiresApprovedExpert
    public ResponseEntity<ProgramRateDTO> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid ProgramRateDTO dto) {
        ProgramRate rate = mapper.toEntity(dto);
        // Never trust client-supplied identity/audit fields: a copied id would make save() merge into someone else's row.
        rate.setId(null);
        rate.setCreatedAt(null);
        rate.setCreatedBy(null);
        rate.setModifiedAt(null);
        rate.setModifiedBy(null);
        
        if (dto.getProgramId() != null) {
            var program = programService.findById(dto.getProgramId());
            if (program.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setProgram(program.get());
        }

        rate.setUserEntity(userService.resolveAuthenticatedUser(
                principal == null ? null : principal.getId(),
                dto.getUserId()
        ));

        // One rating per user per target: rating again updates it in place (200) instead of adding a row.
        var existing = rate.getProgram() == null ? Optional.<ProgramRate>empty()
                : service.findByUserAndProgram(rate.getUserEntity().getId(), rate.getProgram().getId());
        try {
            if (existing.isPresent()) {
                var current = existing.get();
                current.setNote(rate.getNote());
                // null = keep the previous comment (PUT convention); an empty string clears it.
                if (rate.getComment() != null) current.setComment(rate.getComment());
                return ResponseEntity.ok(mapper.toDTO(this.service.save(current)));
            }
            return new ResponseEntity<>(mapper.toDTO(this.service.save(rate)), HttpStatus.CREATED);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // A concurrent request created the same pair between the lookup and the insert.
            if (UniqueRateViolation.matches(ex)) {
                throw new com.bowe.meetstudent.exceptions.ConflictException("Vous avez déjà noté cet élément");
            }
            throw ex;
        }
    }

    @GetMapping
    @Operation(summary = "Get all program ratings (paginated)", description = "Retrieves a paginated list of all program ratings. Default sort: newest first.")
    public Page<ProgramRateDTO> getAll(
            @Parameter(description = "Sort: 'newest', 'oldest', 'highest', 'lowest'") @RequestParam(required = false) String sort,
            @ParameterObject Pageable pageable) {
        
        Pageable sortedPageable = applySorting(pageable, sort);
        return service.findAll(sortedPageable).map(mapper::toDTO);
    }

    @GetMapping("/program/{id}")
    @Operation(summary = "Get all ratings for a specific program", description = "Retrieves a list of all ratings for a program. Default sort: newest first.")
    public ResponseEntity<List<ProgramRateDTO>> getRatesByProgram(
            @PathVariable Integer id,
            @Parameter(description = "Sort: 'newest', 'oldest', 'highest', 'lowest'") @RequestParam(required = false) String sort) {
        
        if (!programService.exists(id)) return ResponseEntity.notFound().build();
        
        Sort sorting = getSort(sort);
        List<ProgramRateDTO> rates = service.findByProgramId(id, sorting)
                .stream()
                .map(mapper::toDTO)
                .toList();
        return ResponseEntity.ok(rates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramRateDTO> getById(@PathVariable Integer id) {
        return service.findById(id)
                .map(rate -> ResponseEntity.ok(mapper.toDTO(rate)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable applySorting(Pageable pageable, String sort) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), getSort(sort));
    }

    private Sort getSort(String sort) {
        if ("highest".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.DESC, "note");
        if ("lowest".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.ASC, "note");
        if ("oldest".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.ASC, "createdAt");
        return Sort.by(Sort.Direction.DESC, "createdAt"); // default newest
    }
}
