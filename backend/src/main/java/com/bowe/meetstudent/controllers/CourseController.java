package com.bowe.meetstudent.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/courses")
@io.swagger.v3.oas.annotations.tags.Tag(name = "10. Courses", description = "Endpoints for managing courses")
public class CourseController {
}
