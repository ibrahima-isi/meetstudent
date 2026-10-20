package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.security.RequiresApprovedExpert;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.CourseRateService;
import com.bowe.meetstudent.services.CourseService;
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
@RequestMapping("/api/v1/course-rates")
@Tag(name = "11. Course Rates", description = "Endpoints for managing course ratings and reviews")
public class CourseRateController {
    private final CourseRateService service;
    private final Mapper<CourseRate, CourseRateDTO> mapper;
    private final CourseService courseService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Rate a course", description = "Allows experts to post a rating and comment for a specific course.")
    @RequiresApprovedExpert
    public ResponseEntity<CourseRateDTO> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid CourseRateDTO dto) {
        CourseRate rate = mapper.toEntity(dto);
        // Never trust client-supplied identity/audit fields: a copied id would make save() merge into someone else's row.
        rate.setId(null);
        rate.setCreatedAt(null);
        rate.setCreatedBy(null);
        rate.setModifiedAt(null);
        rate.setModifiedBy(null);
        
        if (dto.getCourseId() != null) {
            var course = courseService.findById(dto.getCourseId());
            if (course.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setCourse(course.get());
        }

        rate.setUserEntity(userService.resolveAuthenticatedUser(
                principal == null ? null : principal.getId(),
                dto.getUserId()
        ));

        // One rating per user per target: rating again updates it in place (200) instead of adding a row.
        var existing = rate.getCourse() == null ? Optional.<CourseRate>empty()
                : service.findByUserAndCourse(rate.getUserEntity().getId(), rate.getCourse().getId());
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
    @Operation(summary = "Get all course ratings (paginated)", description = "Retrieves a paginated list of all course ratings. Default sort: newest first.")
    public Page<CourseRateDTO> getAll(
            @Parameter(description = "Sort: 'newest', 'oldest', 'highest', 'lowest'") @RequestParam(required = false) String sort,
            @ParameterObject Pageable pageable) {
        
        Pageable sortedPageable = applySorting(pageable, sort);
        return service.findAll(sortedPageable).map(mapper::toDTO);
    }

    @GetMapping("/course/{id}")
    @Operation(summary = "Get all ratings for a specific course", description = "Retrieves a list of all ratings for a course. Default sort: newest first.")
    public ResponseEntity<List<CourseRateDTO>> getRatesByCourse(
            @PathVariable Integer id,
            @Parameter(description = "Sort: 'newest', 'oldest', 'highest', 'lowest'") @RequestParam(required = false) String sort) {
        
        if (!courseService.exists(id)) return ResponseEntity.notFound().build();
        
        Sort sorting = getSort(sort);
        List<CourseRateDTO> rates = service.findByCourseId(id, sorting)
                .stream()
                .map(mapper::toDTO)
                .toList();
        return ResponseEntity.ok(rates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseRateDTO> getById(@PathVariable Integer id) {
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
