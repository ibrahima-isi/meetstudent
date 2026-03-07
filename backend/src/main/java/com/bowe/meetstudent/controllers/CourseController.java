package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
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
@Tag(name = "10. Courses", description = "Endpoints for managing courses")
public class CourseController {

    private final CourseService courseService;
    private final ProgramService programService;
    private final CourseMapper courseMapper;
    private final ModelMapper modelMapper;

    @PostMapping
    @Operation(summary = "Create a new course")
    @ApiResponse(responseCode = "201", description = "Course created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid Program ID")
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
    @Operation(summary = "Get all courses (paginated)")
    @ApiResponse(responseCode = "200", description = "List of courses")
    public Page<CourseDTO> getCourses(@ParameterObject Pageable pageable) {
        return courseService.findAll(pageable).map(courseMapper::toDTO);
    }

    @GetMapping(path = "/{id}")
    @Operation(summary = "Get a course by ID")
    @ApiResponse(responseCode = "302", description = "Course found")
    @ApiResponse(responseCode = "404", description = "Course not found")
    public ResponseEntity<CourseDTO> getCourseById(
            @Parameter(description = "ID of the course to retrieve") @PathVariable int id) {
        return courseService.findById(id)
                .map(course -> new ResponseEntity<>(courseMapper.toDTO(course), HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/name/{name}")
    @Operation(summary = "Search courses by name (paginated)")
    @ApiResponse(responseCode = "200", description = "List of courses matching the name")
    public Page<CourseDTO> getCourseByName(
            @Parameter(description = "Name to search for") @PathVariable String name,
            @ParameterObject Pageable pageable) {
        return courseService.findByName(name, pageable).map(courseMapper::toDTO);
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update a course by ID")
    @ApiResponse(responseCode = "200", description = "Course updated successfully")
    @ApiResponse(responseCode = "404", description = "Course not found")
    @ApiResponse(responseCode = "400", description = "Invalid Program ID")
    public ResponseEntity<CourseDTO> update(
            @RequestBody CourseDTO newCourseDTO,
            @Parameter(description = "ID of the course to update") @PathVariable int id) {
        
        Optional<Course> existingOpt = courseService.findById(id);
        if (existingOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Course existingCourse = existingOpt.get();
        Course mappedUpdate = courseMapper.toEntity(newCourseDTO);
        mappedUpdate.setId(existingCourse.getId());
        
        if (newCourseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(newCourseDTO.getProgramId());
            if (programOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            mappedUpdate.setProgram(programOpt.get());
        } else {
            mappedUpdate.setProgram(null);
        }

        return new ResponseEntity<>(courseMapper.toDTO(courseService.save(mappedUpdate)), HttpStatus.OK);
    }

    @PatchMapping(path = "/{id}")
    @Operation(summary = "Partially update a course by ID")
    @ApiResponse(responseCode = "200", description = "Course patched successfully")
    @ApiResponse(responseCode = "404", description = "Course not found")
    @ApiResponse(responseCode = "400", description = "Invalid Program ID")
    public ResponseEntity<CourseDTO> patch(
            @RequestBody CourseDTO courseDTO,
            @Parameter(description = "ID of the course to patch") @PathVariable int id) {
        
        Optional<Course> existingOpt = courseService.findById(id);
        if (existingOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Course existingCourse = existingOpt.get();
        modelMapper.map(courseDTO, existingCourse);
        
        if (courseDTO.getProgramId() != null) {
            Optional<Program> programOpt = programService.findById(courseDTO.getProgramId());
            if (programOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            existingCourse.setProgram(programOpt.get());
        }

        return new ResponseEntity<>(courseMapper.toDTO(courseService.save(existingCourse)), HttpStatus.OK);
    }

    @DeleteMapping(path = "/{id}")
    @Operation(summary = "Delete a course by ID")
    @ApiResponse(responseCode = "200", description = "Course deleted successfully")
    @ApiResponse(responseCode = "404", description = "Course not found")
    public ResponseEntity<CourseDTO> delete(@Parameter(description = "ID of the course to delete") @PathVariable int id) {
        return courseService.findById(id).map(toDelete -> {
            courseService.delete(toDelete.getId());
            return new ResponseEntity<>(courseMapper.toDTO(toDelete), HttpStatus.OK);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
