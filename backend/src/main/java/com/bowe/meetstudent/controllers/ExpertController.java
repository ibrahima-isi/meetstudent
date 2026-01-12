package com.bowe.meetstudent.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/experts")
@io.swagger.v3.oas.annotations.tags.Tag(name = "11. Experts", description = "Endpoints for expert operations")
public class ExpertController {
}
