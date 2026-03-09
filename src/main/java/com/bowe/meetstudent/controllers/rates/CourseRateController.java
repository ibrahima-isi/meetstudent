package com.bowe.meetstudent.controllers.rates;

import com.bowe.meetstudent.dto.CourseRateDTO;
import com.bowe.meetstudent.entities.rates.CourseRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.CourseRateService;
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

import com.bowe.meetstudent.services.CourseService;
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
@RequestMapping("/api/v1/course-rates")
@Tag(name = "9. Course Rates", description = "Endpoints for managing course ratings and reviews")
public class CourseRateController {
    private final CourseRateService service;
    private final Mapper<CourseRate, CourseRateDTO> mapper;
    private final ModelMapper modelMapper;
    private final CourseService courseService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Rate a course", description = "Allows students or experts to post a rating and comment for a specific course.")
    @ApiResponse(responseCode = "201", description = "Course rate created successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "400", description = "Invalid course or user ID")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<CourseRateDTO> create(@RequestBody CourseRateDTO dto) {
        CourseRate rate = mapper.toEntity(dto);
        
        // Validate and set relationships
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
    @Operation(summary = "Get all course ratings (paginated)", description = "Retrieves a paginated list of all course ratings.")
    @ApiResponse(responseCode = "200", description = "List of course ratings retrieved")
    public Page<CourseRateDTO> getAll(@ParameterObject Pageable pageable) {
        return service.findAll(pageable).map(mapper::toDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a course rating by ID", description = "Retrieves details of a specific course rating using its unique ID.")
    @ApiResponse(responseCode = "200", description = "Course rating found")
    @ApiResponse(responseCode = "404", description = "Course rating not found")
    public ResponseEntity<CourseRateDTO> getById(@PathVariable Integer id) {
        return service.findById(id)
                .map(rate -> ResponseEntity.ok(mapper.toDTO(rate)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/course/{id}")
    @Operation(summary = "Get all ratings for a specific course", description = "Retrieves a list of all ratings posted for the given course ID.")
    @ApiResponse(responseCode = "200", description = "List of course ratings retrieved")
    @ApiResponse(responseCode = "404", description = "Course not found")
    public ResponseEntity<List<CourseRateDTO>> getRatesByCourse(
            @Parameter(description = "ID of the course") @PathVariable Integer id) {
        if (!courseService.exists(id)) return ResponseEntity.notFound().build();
        List<CourseRateDTO> rates = service.findByCourseId(id)
                .stream()
                .map(mapper::toDTO)
                .toList();
        return ResponseEntity.ok(rates);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a course rating by ID", description = "Performs a full update of an existing course rating.")
    @ApiResponse(responseCode = "200", description = "Course rating updated successfully")
    @ApiResponse(responseCode = "404", description = "Course rating not found")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<CourseRateDTO> update(@PathVariable Integer id, @RequestBody CourseRateDTO dto) {
        Optional<CourseRate> existing = service.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();

        CourseRate rate = mapper.toEntity(dto);
        rate.setId(id);

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

        return ResponseEntity.ok(mapper.toDTO(service.save(rate)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a course rating by ID", description = "Updates only the provided fields of an existing course rating.")
    @ApiResponse(responseCode = "200", description = "Course rating patched successfully")
    @ApiResponse(responseCode = "404", description = "Course rating not found")
    @PreAuthorize("hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<CourseRateDTO> patch(@PathVariable Integer id, @RequestBody CourseRateDTO dto) {
        return service.findById(id).map(existing -> {
            modelMapper.map(dto, existing);
            
            if (dto.getCourseId() != null) {
                var course = courseService.findById(dto.getCourseId());
                if (course.isPresent()) existing.setCourse(course.get());
            }
            
            if (dto.getUserId() != null) {
                var user = userService.getUserById(dto.getUserId());
                if (user.isPresent()) existing.setUserEntity(user.get());
            }

            return ResponseEntity.ok(mapper.toDTO(service.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a course rating by ID", description = "Removes a course rating from the system.")
    @ApiResponse(responseCode = "200", description = "Course rating deleted successfully")
    @ApiResponse(responseCode = "404", description = "Course rating not found")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EXPERT') or hasRole('STUDENT')")
    public ResponseEntity<CourseRateDTO> delete(@PathVariable Integer id) {
        return service.findById(id).map(rate -> {
            service.delete(id);
            return ResponseEntity.ok(mapper.toDTO(rate));
        }).orElse(ResponseEntity.notFound().build());
    }
}
