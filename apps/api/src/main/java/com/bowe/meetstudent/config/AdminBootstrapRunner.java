package com.bowe.meetstudent.config;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.services.RefreshTokenService;
import com.bowe.meetstudent.services.UserTokenService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin bootstrap, active on every profile but {@code test}. {@code V2__data.sql} seeds {@code admin@meetstudent.com} with the
 * bcrypt hash of {@code password}; that migration is already applied everywhere so it cannot be edited.
 * On every prod boot this runner:
 * <ol>
 *   <li>requires {@code ADMIN_EMAIL} and an {@code ADMIN_PASSWORD} of at least 12 characters
 *       (it throws, which aborts startup, otherwise);</li>
 *   <li>takes the admin account for {@code ADMIN_EMAIL} (else the seeded default admin, else creates
 *       one) and initializes its email, ADMIN role and bcrypt password from the environment;</li>
 *   <li>replaces the seeded default hash on any remaining account with a random unusable password.</li>
 * </ol>
 * <p>Under {@code prod} the credentials are mandatory. Under {@code dev}/{@code docker} they are
 * optional: when neither {@code ADMIN_EMAIL} nor {@code ADMIN_PASSWORD} is set the runner does nothing,
 * so the seeded {@code admin@meetstudent.com} / {@code password} account keeps working for local
 * development; when they are set the same rules apply as in production (so the default hash is
 * neutralised too). Setting only one of them is always an error.
 *
 * <p>Existing non-seed admin credentials are preserved; {@code ADMIN_PASSWORD} only initializes them.
 * Seed credential changes revoke sessions and sensitive links in the same transaction.
 * Changing {@code ADMIN_EMAIL} later creates a new admin; the previous one is left for manual cleanup.
 */
@Slf4j
@Component
@Profile("!test")
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
    private final RefreshTokenService refreshTokenService;
    private final UserTokenService userTokenService;
    private final EntityManager entityManager;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean required;

    @Autowired
    public AdminBootstrapRunner(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokenService,
                                UserTokenService userTokenService,
                                EntityManager entityManager,
                                @Value("${app.admin.email:}") String adminEmail,
                                @Value("${app.admin.password:}") String adminPassword,
                                Environment environment) {
        this(userRepository, roleRepository, passwordEncoder, refreshTokenService, userTokenService, entityManager, adminEmail, adminPassword,
                environment.acceptsProfiles(Profiles.of("prod")));
    }

    /** @param required true in production: the credentials must be set, or startup aborts. */
    public AdminBootstrapRunner(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokenService,
                                UserTokenService userTokenService,
                                EntityManager entityManager,
                                String adminEmail,
                                String adminPassword,
                                boolean required) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.userTokenService = userTokenService;
        this.entityManager = entityManager;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.required = required;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!required && isBlank(adminEmail) && isBlank(adminPassword)) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set: leaving the seeded development admin account as it is.");
            return;
        }
        validateConfiguration();
        var email = adminEmail.trim();

        var adminRole = roleRepository.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot bootstrap the admin account: role " + ADMIN_ROLE + " does not exist (did Flyway run?)."));

        var admin = userRepository.findByEmailIgnoreCase(email).map(this::lockCurrent)
                .or(() -> userRepository.findByEmailIgnoreCase(DEFAULT_ADMIN_EMAIL).map(this::lockCurrent)
                        .filter(user -> DEFAULT_ADMIN_PASSWORD_HASH.equals(user.getPassword())
                                && user.getRole() != null && ADMIN_ROLE.equals(user.getRole().getName())))
                .orElseGet(() -> UserEntity.builder().firstname("System").lastname("Admin").build());

        if (admin.getId() != null && (admin.getRole() == null || !ADMIN_ROLE.equals(admin.getRole().getName()))) {
            throw new IllegalStateException("ADMIN_EMAIL belongs to a non-admin account; bootstrap will not promote it.");
        }

        boolean changed = false;
        if (!email.equals(admin.getEmail())) {
            admin.setEmail(email);
            changed = true;
        }
        if (admin.getRole() == null || !ADMIN_ROLE.equals(admin.getRole().getName())) {
            admin.setRole(adminRole);
            changed = true;
        }
        if (admin.getPassword() == null || DEFAULT_ADMIN_PASSWORD_HASH.equals(admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(adminPassword));
            if (admin.getId() != null) revokeCredentials(admin);
            changed = true;
        }
        if (!admin.isEmailVerified()) {
            // The operator owns this address by construction; the admin never goes through the email flow.
            admin.setEmailVerified(true);
            changed = true;
        }
        if (changed) {
            userRepository.save(admin);
            log.info("Admin account bootstrapped/updated for {}", email);
        }

        // Anything still carrying the published default hash must not be usable.
        for (var candidate : userRepository.findAllByPassword(DEFAULT_ADMIN_PASSWORD_HASH)) {
            var other = lockCurrent(candidate);
            if (!DEFAULT_ADMIN_PASSWORD_HASH.equals(other.getPassword())) continue;
            other.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            revokeCredentials(other);
            userRepository.save(other);
            log.warn("Neutralised default seeded password on account {}", other.getEmail());
        }
    }

    private UserEntity lockCurrent(UserEntity user) {
        var current = userRepository.findByIdForUpdate(user.getId()).orElseThrow();
        // A credential change may have committed while this startup waited for the row lock.
        entityManager.refresh(current);
        return current;
    }

    private void revokeCredentials(UserEntity user) {
        user.setPasswordVersion(Math.incrementExact(user.getPasswordVersion()));
        refreshTokenService.deleteByUserId(user.getId());
        for (var purpose : UserTokenPurpose.values()) {
            userTokenService.consumeOutstanding(user.getId(), purpose);
        }
    }

    private void validateConfiguration() {
        if (isBlank(adminEmail)) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL must be set" + (required ? " when running with the 'prod' profile." : " together with ADMIN_PASSWORD."));
        }
        if (isBlank(adminPassword)) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be set" + (required ? " when running with the 'prod' profile." : " together with ADMIN_EMAIL."));
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
