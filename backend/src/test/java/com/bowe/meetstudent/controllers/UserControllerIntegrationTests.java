package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.UserDTO;
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
public class UserControllerIntegrationTests {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserControllerIntegrationTests(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    public void testThatCreateUserReturnStatusCode201Created() throws Exception {
        UserDTO userDTO = TestDataUtil.createUserDto();

        String json = objectMapper.writeValueAsString(userDTO);
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
        UserDTO user = TestDataUtil.createUserDto();

        String json = objectMapper.writeValueAsString(user);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.id").isNumber()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.firstname").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.lastname").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.email").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.password").doesNotExist()
        );
    }

}
