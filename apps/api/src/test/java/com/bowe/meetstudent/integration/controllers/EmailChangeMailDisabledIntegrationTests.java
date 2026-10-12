package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default test configuration: email delivery is disabled, so a change cannot be confirmed and is refused up front. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailChangeMailDisabledIntegrationTests {

    private static final String PASSWORD = "S3cure-test-pass";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserTokenRepository userTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        userTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void requestingAChangeAnswers503AndLeavesNoTokenBehind() throws Exception {
        var role = roleRepository.findByName("ROLE_STUDENT")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_STUDENT").description("Student").build()));
        var user = userService.saveUser(UserEntity.builder().firstname("Jane").lastname("Doe").email("disabled@example.com")
                .password(PASSWORD).role(role).build(), passwordEncoder);
        var login = mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"disabled@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var auth = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/users/" + user.getId() + "/email-change").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"other@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isServiceUnavailable());

        assertThat(userTokenRepository.count()).isZero();
    }
}
