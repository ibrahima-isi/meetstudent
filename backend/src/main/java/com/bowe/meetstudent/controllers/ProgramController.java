package com.bowe.meetstudent.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/programs")
@io.swagger.v3.oas.annotations.tags.Tag(name = "12. Programs", description = "Endpoints for managing educational programs")
public class ProgramController {
}
