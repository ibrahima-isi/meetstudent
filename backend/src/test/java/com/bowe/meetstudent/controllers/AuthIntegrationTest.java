package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.security.JwtDecoder;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Use standard 'test' profile
@Transactional // Rollback DB after each test
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Ensure roles exist (Flyway might have run, but H2 can be tricky with profiles)
        // We check and insert if needed just to be safe for the test context
        if (roleRepository.ROLE_STUDENT.isEmpty()) {
            roleRepository.save(Role.builder().name("ROLE_USER").description("User").build());
        }
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        // 1. Create a User in DB
        String email = "integration@test.com";
        String rawPassword = "password123";
        
        // We create entity directly or via service
        // Creating role first
        Role userRole = roleRepository.ROLE_STUDENT.orElseThrow();

        // Note: Using Service is better to ensure encoding, but we can do it manually for speed/control
        // However, userService.saveUser encodes password.
        
        com.bowe.meetstudent.entities.UserEntity user = com.bowe.meetstudent.entities.UserEntity.builder()
                .email(email)
                .password(rawPassword) // Service will encode this
                .firstname("Test")
                .lastname("User")
                .role(userRole)
                .build();
        
        userService.saveUser(user, passwordEncoder);

        // 2. Perform Login Request
        LoginRequest loginRequest = LoginRequest.builder()
                .username(email)
                .password(rawPassword)
                .build();

        mockMvc.perform(post("/api/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void shouldFailLogin_whenPasswordIsWrong() throws Exception {
        // 1. Create a User
        String email = "wrongpass@test.com";
        Role userRole = roleRepository.ROLE_STUDENT.orElseThrow();
        
        com.bowe.meetstudent.entities.UserEntity user = com.bowe.meetstudent.entities.UserEntity.builder()
                .email(email)
                .password("correctPassword")
                .firstname("Test")
                .lastname("User")
                .role(userRole)
                .build();
        
        userService.saveUser(user, passwordEncoder);

        // 2. Try Login with WRONG password
        LoginRequest loginRequest = LoginRequest.builder()
                .username(email)
                .password("wrongPassword")
                .build();

        mockMvc.perform(post("/api/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized()); // Expect 401
    }

    @Test
    void shouldFailAccessSecuredEndpoint_withoutToken() throws Exception {
        mockMvc.perform(get("/api/users")) // Assuming this is secured now
                .andExpect(status().isForbidden()); // Expect 403
    }
}
