package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/courses")
@Tag(name = "10. Courses", description = "Endpoints for managing individual subjects or modules (Courses)")
public class CourseController {

    private final CourseService courseService;
    private final ProgramService programService;
    private final CourseMapper courseMapper;
    private final ModelMapper modelMapper;
    private final CourseRateService courseRateService;

    @PostMapping
    @Operation(summary = "Create a new course", description = "Adds a new course to the system, optionally linking it to a program.")
    @ApiResponse(responseCode = "201", description = "Course created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid Program ID or input data")
    public ResponseEntity<CourseDTO> create(@RequestBody CourseDTO courseDTO) {
        Course course = courseMapper.toEntity(courseDTO);
        
        if (courseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(courseDTO.getProgramId());
            if (programOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            course.setProgram(programOpt.get());
        } else {
            course.setProgram(null);
        }

        Course savedCourse = courseService.save(course);
        return new ResponseEntity<>(courseMapper.toDTO(savedCourse), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all courses (paginated)", description = "Provides a paginated list of all courses, including their average ratings. Can be sorted by rating (most/less).")
    @ApiResponse(responseCode = "200", description = "List of courses retrieved")
    public Page<CourseDTO> getCourses(
            @Parameter(description = "Sort by rate: 'most' for highest rated, 'less' for lowest rated") 
            @RequestParam(required = false) String sortRate,
            @ParameterObject Pageable pageable) {
        
        Page<Course> coursePage;
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
    @ApiResponse(responseCode = "302", description = "Course found")
    @ApiResponse(responseCode = "404", description = "Course not found")
    public ResponseEntity<CourseDTO> getCourseById(
            @Parameter(description = "ID of the course to retrieve") @PathVariable int id) {
        return courseService.findById(id)
                .map(course -> {
                    CourseDTO dto = courseMapper.toDTO(course);
                    dto.setAverageRate(courseRateService.getAverageNoteByCourseId(course.getId()));
                    return new ResponseEntity<>(dto, HttpStatus.FOUND);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search courses by name (paginated)", description = "Searches for courses whose name contains the given string.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved")
    public Page<CourseDTO> getCourseByName(
            @Parameter(description = "Name to search for") @PathVariable String name,
            @ParameterObject Pageable pageable) {
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
    public ResponseEntity<CourseDTO> update(
            @RequestBody CourseDTO newCourseDTO,
            @Parameter(description = "ID of the course to update") @PathVariable int id) {
        
        Course updates = courseMapper.toEntity(newCourseDTO);
        if (newCourseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(newCourseDTO.getProgramId());
            if (programOpt.isEmpty()) return ResponseEntity.badRequest().build();
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
    public ResponseEntity<CourseDTO> patch(
            @RequestBody CourseDTO courseDTO,
            @Parameter(description = "ID of the course to patch") @PathVariable int id) {
        
        Course updates = courseMapper.toEntity(courseDTO);
        if (courseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(courseDTO.getProgramId());
            if (programOpt.isPresent()) updates.setProgram(programOpt.get());
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
            courseService.delete(toDelete.getId());
            return new ResponseEntity<>(courseMapper.toDTO(toDelete), HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
