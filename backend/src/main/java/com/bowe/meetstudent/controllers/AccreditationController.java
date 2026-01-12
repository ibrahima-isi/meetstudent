package com.bowe.meetstudent.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/accreditations")
@io.swagger.v3.oas.annotations.tags.Tag(name = "8. Accreditations", description = "Endpoints for managing accreditations")
public class AccreditationController {
}
