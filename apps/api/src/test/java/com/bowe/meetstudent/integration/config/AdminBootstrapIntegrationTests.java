package com.bowe.meetstudent.integration.config;

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

/**
 * Exercises the bootstrap logic against a real JPA repository (H2). The runner is built by hand
 * because the bean itself is prod-only; the prod wiring is covered by
 * {@link AdminBootstrapProdProfileIntegrationTests}.
 */
@SpringBootTest
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

    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Admin").build()));
    }

    private AdminBootstrapRunner runner(String email, String password) {
        return new AdminBootstrapRunner(userRepository, roleRepository, passwordEncoder, email, password);
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
    void isNotRegisteredOutsideProdProfile() {
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
        assertTrue(userRepository.findByEmailIgnoreCase(AdminBootstrapRunner.DEFAULT_ADMIN_EMAIL).isEmpty());
    }

    @Test
    void createsAdmin_whenNoSeededAdminExists() throws Exception {
        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        var admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword()));
        assertEquals("ROLE_ADMIN", admin.getRole().getName());
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
    void rotatesPassword_whenAdminPasswordChanges() throws Exception {
        seededDefaultAdmin();
        runner(ADMIN_EMAIL, ADMIN_PASSWORD).run(null);

        runner(ADMIN_EMAIL, "an-even-better-pass").run(null);

        var admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches("an-even-better-pass", admin.getPassword()));
        assertFalse(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword()));
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
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, owner.getPassword()));
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
