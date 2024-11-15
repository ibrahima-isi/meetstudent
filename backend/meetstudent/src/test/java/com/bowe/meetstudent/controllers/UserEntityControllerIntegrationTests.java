package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
public class UserEntityControllerIntegrationTests {

    private final MockMvc mockMvc;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserEntityControllerIntegrationTests(MockMvc mockMvc, UserService userService, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Test
    public void testThatCreateUserReturnStatusCode201Created() throws Exception {
        UserEntity userEntity = TestDataUtil.createUser();

        String json = objectMapper.writeValueAsString(userEntity);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    public void testThatUserCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        UserEntity userEntity = TestDataUtil.createUser();

        String json = objectMapper.writeValueAsString(userEntity);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.id").isNumber()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.firstname").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.email").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.lastname").value("diallo")
                ).andExpect(
                MockMvcResultMatchers.jsonPath("$.password").exists()
        );
    }

}
