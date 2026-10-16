package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.dto.CourseAccreditationDTO;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.mappers.implementations.CourseAccreditationMapper;
import com.bowe.meetstudent.services.CourseAccreditationService;
import com.bowe.meetstudent.services.CourseRateService;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.bowe.meetstudent.dto.validation.OnCreate;
import jakarta.validation.groups.Default;


import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/courses")
@Tag(name = "06. Courses", description = "Endpoints for managing individual subjects or modules (Courses)")
public class CourseController {

    private final CourseService courseService;
    private final ProgramService programService;
    private final CourseMapper courseMapper;
    private final ModelMapper modelMapper;
    private final CourseRateService courseRateService;
    private final CourseAccreditationService courseAccreditationService;
    private final CourseAccreditationMapper courseAccreditationMapper;

    @PostMapping(path = "/{courseId}/accreditations/{accreditationId}")
    @Operation(summary = "Link an accreditation to a course", description = "Creates a course-specific accreditation association with validity years.")
    @ApiResponse(responseCode = "201", description = "Accreditation linked successfully")
    @ApiResponse(responseCode = "404", description = "Course or accreditation not found")
    @ApiResponse(responseCode = "409", description = "Accreditation is already linked to this course")
    public ResponseEntity<CourseAccreditationDTO> linkAccreditation(
            @PathVariable Integer courseId,
            @PathVariable Integer accreditationId,
            @RequestParam Integer startsAt,
            @RequestParam Integer endsAt) {
        var linked = courseAccreditationService.add(courseId, accreditationId, startsAt, endsAt);
        return new ResponseEntity<>(courseAccreditationMapper.toDTO(linked), HttpStatus.CREATED);
    }

    @GetMapping(path = "/{courseId}/accreditations/{accreditationId}")
    @Operation(summary = "Get a course accreditation", description = "Retrieves one course-specific accreditation association.")
    @ApiResponse(responseCode = "200", description = "Association found")
    @ApiResponse(responseCode = "404", description = "Association not found")
    public ResponseEntity<CourseAccreditationDTO> getAccreditation(
            @PathVariable Integer courseId,
            @PathVariable Integer accreditationId) {
        return courseAccreditationService.findById(courseId, accreditationId)
                .map(link -> ResponseEntity.ok(courseAccreditationMapper.toDTO(link)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/{courseId}/accreditations")
    @Operation(summary = "Get course accreditations", description = "Lists the accreditations linked directly to a course.")
    @ApiResponse(responseCode = "200", description = "Associations listed")
    @ApiResponse(responseCode = "404", description = "Course not found")
    public ResponseEntity<Page<CourseAccreditationDTO>> getAccreditations(
            @PathVariable Integer courseId, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(courseAccreditationService.findByCourseId(courseId, pageable)
                .map(courseAccreditationMapper::toDTO));
    }

    @DeleteMapping(path = "/{courseId}/accreditations/{accreditationId}")
    @Operation(summary = "Unlink an accreditation from a course", description = "Deletes only the selected course-accreditation association.")
    @ApiResponse(responseCode = "204", description = "Accreditation unlinked successfully")
    @ApiResponse(responseCode = "404", description = "Association not found")
    public ResponseEntity<Void> unlinkAccreditation(
            @PathVariable Integer courseId,
            @PathVariable Integer accreditationId) {
        courseAccreditationService.remove(courseId, accreditationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @Operation(summary = "Create a new course", description = "Adds a new course to the system, optionally linking it to a program.")
    @ApiResponse(responseCode = "201", description = "Course created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid Program ID or input data")
    @ApiResponse(responseCode = "409", description = "Course code already exists")
    public ResponseEntity<CourseDTO> create(@RequestBody @Validated({Default.class, OnCreate.class}) CourseDTO courseDTO) {
        Course course = courseMapper.toEntity(courseDTO);
        
        if (courseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(courseDTO.getProgramId());
            if (programOpt.isEmpty()) {
                throw new FieldValidationException("programId", "Program not found");
            }
            course.setProgram(programOpt.get());
        } else {
            course.setProgram(null);
        }

        Course savedCourse = courseService.save(course);
        return new ResponseEntity<>(courseMapper.toDTO(savedCourse), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all courses (paginated)", description = "Provides a paginated list of all courses, including their average ratings. Can be sorted by rating (most/less). Each course carries its program (programId, programName; null when detached). Without ?sort= results are ordered by name, then id; an explicit ?sort= replaces that order and an unknown property answers 400. With ?sortRate= the rate comes first and that order only breaks ties.")
    @ApiResponse(responseCode = "200", description = "List of courses retrieved")
    public Page<CourseDTO> getCourses(
            @Parameter(description = "Sort by rate: 'most' for highest rated, 'less' for lowest rated") 
            @RequestParam(required = false) String sortRate,
            @ParameterObject @SortDefault(sort = {"name", "id"}) Pageable pageable) {
        
        Page<Course> coursePage;
        // The rate queries keep AVG as the first ORDER BY key; the page sort (name, id by default) is appended as the tie-break.
        if ("most".equalsIgnoreCase(sortRate)) {
            coursePage = courseService.findAllOrderByRateDesc(pageable);
        } else if ("less".equalsIgnoreCase(sortRate)) {
            coursePage = courseService.findAllOrderByRateAsc(pageable);
        } else {
            coursePage = courseService.findAll(pageable);
        }

        return coursePage.map(course -> {
            CourseDTO dto = courseMapper.toDTO(course);
            dto.setAverageRate(courseRateService.getAverageNoteByCourseId(course.getId()));
            return dto;
        });
    }

    @GetMapping(path = "/{id}")
    @Operation(summary = "Get a course by ID", description = "Retrieves details of a specific course using its unique ID.")
    @ApiResponse(responseCode = "200", description = "Course found")
    @ApiResponse(responseCode = "404", description = "Course not found")
    public ResponseEntity<CourseDTO> getCourseById(
            @Parameter(description = "ID of the course to retrieve") @PathVariable int id) {
        return courseService.findById(id)
                .map(course -> {
                    CourseDTO dto = courseMapper.toDTO(course);
                    dto.setAverageRate(courseRateService.getAverageNoteByCourseId(course.getId()));
                    return new ResponseEntity<>(dto, HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search courses by name (paginated)", description = "Searches for courses whose name contains the given string. Each result carries its program (programId, programName; null when detached). Without ?sort= results are ordered by name, then id; an explicit ?sort= replaces that order and an unknown property answers 400.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved")
    public Page<CourseDTO> getCourseByName(
            @Parameter(description = "Name to search for") @PathVariable String name,
            @ParameterObject @SortDefault(sort = {"name", "id"}) Pageable pageable) {
        return courseService.findByName(name, pageable).map(course -> {
            CourseDTO dto = courseMapper.toDTO(course);
            dto.setAverageRate(courseRateService.getAverageNoteByCourseId(course.getId()));
            return dto;
        });
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update a course by ID", description = "Performs a full update of an existing course.")
    @ApiResponse(responseCode = "200", description = "Course updated successfully")
    @ApiResponse(responseCode = "404", description = "Course not found")
    @ApiResponse(responseCode = "400", description = "Invalid Program ID")
    @ApiResponse(responseCode = "409", description = "Course code already exists")
    public ResponseEntity<CourseDTO> update(
            @RequestBody @Validated CourseDTO newCourseDTO,
            @Parameter(description = "ID of the course to update") @PathVariable int id) {
        
        Course updates = courseMapper.toEntity(newCourseDTO);
        if (newCourseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(newCourseDTO.getProgramId());
            if (programOpt.isEmpty()) throw new FieldValidationException("programId", "Program not found");
            updates.setProgram(programOpt.get());
        }

        Course saved = courseService.patch(id, updates);
        return ResponseEntity.ok(courseMapper.toDTO(saved));
    }

    @PatchMapping(path = "/{id}")
    @Operation(summary = "Partially update a course by ID", description = "Updates only the provided fields of an existing course.")
    @ApiResponse(responseCode = "200", description = "Course patched successfully")
    @ApiResponse(responseCode = "404", description = "Course not found")
    @ApiResponse(responseCode = "400", description = "Invalid Program ID")
    @ApiResponse(responseCode = "409", description = "Course code already exists")
    public ResponseEntity<CourseDTO> patch(
            @RequestBody @Validated CourseDTO courseDTO,
            @Parameter(description = "ID of the course to patch") @PathVariable int id) {
        
        Course updates = courseMapper.toEntity(courseDTO);
        if (courseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(courseDTO.getProgramId());
            if (programOpt.isEmpty()) throw new FieldValidationException("programId", "Program not found");
            updates.setProgram(programOpt.get());
        }

        Course saved = courseService.patch(id, updates);
        return ResponseEntity.ok(courseMapper.toDTO(saved));
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Delete a course by ID", description = "Removes a course from the system.")
    @ApiResponse(responseCode = "200", description = "Course deleted successfully")
    @ApiResponse(responseCode = "404", description = "Course not found")
    public ResponseEntity<CourseDTO> delete(@Parameter(description = "ID of the course to delete") @PathVariable int id) {
        return courseService.findById(id).map(toDelete -> {
            // Map to DTO BEFORE deleting: the entity's lazy collections cannot be
            // resolved once it has been removed from the session.
            CourseDTO dto = courseMapper.toDTO(toDelete);
            courseService.delete(toDelete.getId());
            return new ResponseEntity<>(dto, HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
