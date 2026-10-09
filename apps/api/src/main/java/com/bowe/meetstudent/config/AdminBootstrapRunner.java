package com.bowe.meetstudent.config;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Production-only admin bootstrap. {@code V2__data.sql} seeds {@code admin@meetstudent.com} with the
 * bcrypt hash of {@code password}; that migration is already applied everywhere so it cannot be edited.
 * On every prod boot this runner:
 * <ol>
 *   <li>requires {@code ADMIN_EMAIL} and an {@code ADMIN_PASSWORD} of at least 12 characters
 *       (it throws, which aborts startup, otherwise);</li>
 *   <li>takes the admin account for {@code ADMIN_EMAIL} (else the seeded default admin, else creates
 *       one) and sets its email, ADMIN role and bcrypt password from the environment;</li>
 *   <li>replaces the seeded default hash on any remaining account with a random unusable password.</li>
 * </ol>
 * Idempotent: an unchanged password is not re-hashed; changing {@code ADMIN_PASSWORD} rotates it.
 * Changing {@code ADMIN_EMAIL} later creates a new admin; the previous one is left for manual cleanup.
 */
@Slf4j
@Component
@Profile("prod")
public class AdminBootstrapRunner implements ApplicationRunner {

    public static final String DEFAULT_ADMIN_EMAIL = "admin@meetstudent.com";
    /** bcrypt hash of "password", as inserted by V2__data.sql. */
    public static final String DEFAULT_ADMIN_PASSWORD_HASH =
            "$2a$10$upwmHP5SvZQCBWozT9IVLeFWXo5MUE8J15P02YVVevyGlt90UGE.m";
    public static final int MIN_PASSWORD_LENGTH = 12;

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.email:}") String adminEmail,
                                @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateConfiguration();
        var email = adminEmail.trim();

        var adminRole = roleRepository.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot bootstrap the admin account: role " + ADMIN_ROLE + " does not exist (did Flyway run?)."));

        var admin = userRepository.findByEmailIgnoreCase(email)
                .or(() -> userRepository.findByEmailIgnoreCase(DEFAULT_ADMIN_EMAIL))
                .orElseGet(() -> UserEntity.builder().firstname("System").lastname("Admin").build());

        boolean changed = false;
        if (!email.equals(admin.getEmail())) {
            admin.setEmail(email);
            changed = true;
        }
        if (admin.getRole() == null || !ADMIN_ROLE.equals(admin.getRole().getName())) {
            admin.setRole(adminRole);
            changed = true;
        }
        if (admin.getPassword() == null || !passwordEncoder.matches(adminPassword, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(adminPassword));
            changed = true;
        }
        if (changed) {
            userRepository.save(admin);
            log.info("Admin account bootstrapped/updated for {}", email);
        }

        // Anything still carrying the published default hash must not be usable.
        for (var other : userRepository.findAllByPassword(DEFAULT_ADMIN_PASSWORD_HASH)) {
            other.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            userRepository.save(other);
            log.warn("Neutralised default seeded password on account {}", other.getEmail());
        }
    }

    private void validateConfiguration() {
        if (adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL must be set when running with the 'prod' profile.");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be set when running with the 'prod' profile.");
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }
    }
}
