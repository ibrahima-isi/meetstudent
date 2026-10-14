package com.bowe.meetstudent.integration.config;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.bowe.meetstudent.services.UserTokenService;
import com.bowe.meetstudent.services.RefreshTokenService;
import jakarta.persistence.EntityManager;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.http.MediaType;
import com.bowe.meetstudent.config.AdminBootstrapRunner;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the bootstrap logic against a real JPA repository (H2). The runner is built by hand
 * because the bean is disabled under test; the prod wiring is covered by
 * {@link AdminBootstrapProdProfileIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminBootstrapIntegrationTests {

    private static final String ADMIN_EMAIL = "owner@meetstudent.example";
    private static final String ADMIN_PASSWORD = "S3cure-admin-pass";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserTokenService userTokenService;

    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private EntityManager entityManager;

    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Admin").build()));
    }

    private AdminBootstrapRunner runner(String email, String password) {
        return new AdminBootstrapRunner(userRepository, roleRepository, passwordEncoder, refreshTokenService, userTokenService, entityManager, email, password, true);
    }

    private UserEntity seededDefaultAdmin() {
        return userRepository.save(UserEntity.builder()
                .firstname("System").lastname("Admin")
                .email(AdminBootstrapRunner.DEFAULT_ADMIN_EMAIL)
                .password(AdminBootstrapRunner.DEFAULT_ADMIN_PASSWORD_HASH)
                .role(adminRole).build());
    }

    @Test
    void seededHashReallyIsTheHashOfPassword() {
        assertTrue(passwordEncoder.matches("password", AdminBootstrapRunner.DEFAULT_ADMIN_PASSWORD_HASH));
    }

    @Test
    void isNotRegisteredUnderTestProfile() {
        assertTrue(context.getBeansOfType(AdminBootstrapRunner.class).isEmpty());
    }

    @Test
    void repurposesSeededAdmin_withEnvEmailAndPassword() throws Exception {
        var seeded = seededDefaultAdmin();

        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        var admin = userRepository.findById(seeded.getId()).orElseThrow();
        assertEquals(ADMIN_EMAIL, admin.getEmail());
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword()));
        assertEquals("ROLE_ADMIN", admin.getRole().getName());
        assertTrue(admin.isEmailVerified(), "the bootstrapped admin must not be asked to verify its email");
        assertTrue(userRepository.findByEmailIgnoreCase(AdminBootstrapRunner.DEFAULT_ADMIN_EMAIL).isEmpty());
    }

    @Test
    void createsAdmin_whenNoSeededAdminExists() throws Exception {
        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        var admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword()));
        assertEquals("ROLE_ADMIN", admin.getRole().getName());
        assertTrue(admin.isEmailVerified());
    }

    @Test
    void marksAnExistingUnverifiedAdminVerified_evenWhenNothingElseChanges() throws Exception {
        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);
        var admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        admin.setEmailVerified(false);
        userRepository.save(admin);

        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        assertTrue(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().isEmailVerified());
    }

    @Test
    void isIdempotent_whenRerunWithSameValues() throws Exception {
        seededDefaultAdmin();
        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);
        var firstHash = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getPassword();
        long count = userRepository.count();

        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        var again = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        assertEquals(firstHash, again.getPassword(), "an unchanged password must not be re-hashed");
        assertEquals(count, userRepository.count());
    }

    @Test
    void preservesPassword_whenAdminPasswordEnvironmentChanges() throws Exception {
        seededDefaultAdmin();
        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        runner(ADMIN_EMAIL, "an-even-better-pass").run(null);

        login(ADMIN_EMAIL, ADMIN_PASSWORD, 200);
        login(ADMIN_EMAIL, "an-even-better-pass", 401);
    }

    private void login(String email, String password, int status) throws Exception {
        mvc.perform(post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(status));
    }

    @Test
    void neutralisesEveryOtherAccountStillCarryingTheDefaultHash() throws Exception {
        seededDefaultAdmin();
        var stray = userRepository.save(UserEntity.builder()
                .firstname("Stray").lastname("Copy").email("stray@example.com")
                .password(AdminBootstrapRunner.DEFAULT_ADMIN_PASSWORD_HASH).role(adminRole).build());
        // The env email already belongs to someone else, so the seeded row is not repurposed.
        userRepository.save(UserEntity.builder()
                .firstname("Owner").lastname("Existing").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode("old-unrelated-pass")).role(adminRole).build());

        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        assertTrue(userRepository.findAll().stream()
                .noneMatch(u -> AdminBootstrapRunner.DEFAULT_ADMIN_PASSWORD_HASH.equals(u.getPassword())));
        for (var u : userRepository.findAll()) {
            assertFalse(passwordEncoder.matches("password", u.getPassword()),
                    u.getEmail() + " can still log in with 'password'");
        }
        assertNotNull(userRepository.findById(stray.getId()).orElseThrow().getPassword());
        var owner = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches("old-unrelated-pass", owner.getPassword()));
    }

    @Test
    void refusesToPromoteAnExistingStudentAtTheConfiguredAddress() throws Exception {
        var student = roleRepository.save(Role.builder().name("ROLE_STUDENT").description("Student").build());
        userRepository.save(UserEntity.builder().firstname("Regular").lastname("User").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode("student-password")).role(student).build());

        assertThrows(IllegalStateException.class, () -> runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null));
        login(ADMIN_EMAIL, "student-password", 200);
        login(ADMIN_EMAIL, ADMIN_PASSWORD, 401);
    }

    @Test
    void doesNotRepurposeTheDefaultAddressAfterItsPasswordWasChanged() throws Exception {
        var existing = seededDefaultAdmin();
        existing.setPassword(passwordEncoder.encode("changed-seed-password"));
        userRepository.save(existing);

        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        login(AdminBootstrapRunner.DEFAULT_ADMIN_EMAIL, "changed-seed-password", 200);
        login(ADMIN_EMAIL, ADMIN_PASSWORD, 200);
    }

    @Test
    void neutralisingSeedCredentialsRevokesExistingSessionsAndSensitiveLinks() throws Exception {
        var seed = seededDefaultAdmin();
        var response = mvc.perform(post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@meetstudent.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var session = new ObjectMapper().readTree(response);
        var reset = userTokenService.issue(seed, UserTokenPurpose.PASSWORD_RESET,
                Duration.ofMinutes(30));
        var change = userTokenService.issue(seed, UserTokenPurpose.EMAIL_CHANGE,
                Duration.ofMinutes(30), "unwanted@example.com");

        runner(AdminBootstrapRunner.DEFAULT_ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        mvc.perform(get("/api/v1/users/id/" + seed.getId())
                        .header("Authorization", "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + reset + "\",\"password\":\"unwanted-password\",\"confirmedPassword\":\"unwanted-password\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + change + "\"}"))
                .andExpect(status().isBadRequest());
        login(AdminBootstrapRunner.DEFAULT_ADMIN_EMAIL, ADMIN_PASSWORD, 200);
    }

    @Test
    void failsFast_whenRoleAdminIsMissing() {
        // Fresh H2 without the role: remove it for this test (transaction is rolled back).
        userRepository.deleteAll();
        roleRepository.delete(adminRole);
        roleRepository.flush();

        assertThrows(IllegalStateException.class, () -> runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null));
    }
}
