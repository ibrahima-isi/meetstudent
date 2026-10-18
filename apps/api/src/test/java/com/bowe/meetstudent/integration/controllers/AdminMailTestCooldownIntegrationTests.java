package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mail.SendOutcome;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default cooldown (10 s per admin): the self-test cannot be used to hammer the SMTP server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
class AdminMailTestCooldownIntegrationTests {

    private static final String URL = "/api/v1/admin/mail/test";

    @Autowired private MockMvc mockMvc;
    @Autowired private CapturingEmailSender mailbox;
    @Autowired private UserRepository userRepository;
    @Autowired private UserTokenRepository userTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Integer firstAdmin;
    private Integer secondAdmin;

    @BeforeEach
    void setUp() {
        mailbox.clear();
        var admin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Admin").build()));
        firstAdmin = userService.saveUser(UserEntity.builder().firstname("A").lastname("One").email("one@example.org")
                .password("S3cure-test-pass").role(admin).build(), passwordEncoder).getId();
        secondAdmin = userService.saveUser(UserEntity.builder().firstname("B").lastname("Two").email("two@example.org")
                .password("S3cure-test-pass").role(admin).build(), passwordEncoder).getId();
    }

    @AfterEach
    void cleanUp() {
        userTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        mailbox.clear();
    }

    @Test
    void aSecondCallWithinTheCooldownIs429WithRetryAfterAndSendsNothing() throws Exception {
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(firstAdmin, "ROLE_ADMIN"))).andExpect(status().isOk());
        assertThat(mailbox.all()).hasSize(1);

        var response = mockMvc.perform(post(URL).with(TestDataUtil.mockUser(firstAdmin, "ROLE_ADMIN")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse();

        assertThat(Long.parseLong(response.getHeader("Retry-After"))).isBetween(1L, 10L);
        assertThat(mailbox.all()).hasSize(1);
    }

    @Test
    void theCooldownIsPerAdminAndAFailedTestCountsToo() throws Exception {
        mailbox.respondWith(SendOutcome.CONNECTION_FAILED);
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(firstAdmin, "ROLE_ADMIN"))).andExpect(status().isBadGateway());
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(firstAdmin, "ROLE_ADMIN"))).andExpect(status().isTooManyRequests());

        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(secondAdmin, "ROLE_ADMIN"))).andExpect(status().isBadGateway());
        assertThat(mailbox.all()).hasSize(2);
    }
}
