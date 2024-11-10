package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.services.SchoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
public class SchoolControllerIntegrationTests {

    private final SchoolService schoolService;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;


    @Autowired
    public SchoolControllerIntegrationTests(SchoolService schoolService, MockMvc mockMvc, ObjectMapper objectMapper) {
        this.schoolService = schoolService;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

}
