package com.bowe.meetstudent.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/admins")
@io.swagger.v3.oas.annotations.tags.Tag(name = "9. Admins", description = "Endpoints for admin operations")
public class AdminController {
}
