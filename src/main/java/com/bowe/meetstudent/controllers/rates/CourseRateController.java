package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.CourseRateService;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final ModelMapper modelMapper;
    private final CourseService courseService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Rate a course", description = "Allows experts to post a rating and comment for a specific course.")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<CourseRateDTO> create(@RequestBody CourseRateDTO dto) {
        CourseRate rate = mapper.toEntity(dto);
        
        if (dto.getCourseId() != null) {
            var course = courseService.findById(dto.getCourseId());
            if (course.isEmpty()) return ResponseEntity.badRequest().build();
            rate.setCourse(course.get());
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
