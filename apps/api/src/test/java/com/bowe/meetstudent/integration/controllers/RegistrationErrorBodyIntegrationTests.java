package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.repositories.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registration business-rule failures must carry the same {field: message} body as bean validation. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationErrorBodyIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoleRepository roleRepository;

    private String body(String email, String password, String confirmation) {
        return "{\"firstname\":\"A\",\"lastname\":\"B\",\"email\":\"" + email + "\","
                + "\"password\":\"" + password + "\",\"confirmedPassword\":\"" + confirmation + "\"}";
    }

    private void ensureStudentRole() {
        roleRepository.findByName("ROLE_STUDENT")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_STUDENT").build()));
    }

    @Test
    void passwordMismatchReturnsFieldMessage() throws Exception {
        ensureStudentRole();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("new@example.com", "password123", "different123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.confirmedPassword").isNotEmpty());
    }

    @Test
    void duplicateEmailReturnsFieldMessage() throws Exception {
        ensureStudentRole();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup@example.com", "password123", "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup@example.com", "password123", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").isNotEmpty());
    }
}
