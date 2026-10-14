package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.AdminBootstrapRunner;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminBootstrapRunnerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);

    /** Production: the credentials are mandatory. */
    private AdminBootstrapRunner runnerWith(String email, String password) {
        return new AdminBootstrapRunner(userRepository, roleRepository, new BCryptPasswordEncoder(), null, null, null, email, password, true);
    }

    /** dev / docker: the credentials are optional. */
    private AdminBootstrapRunner optionalRunnerWith(String email, String password) {
        return new AdminBootstrapRunner(userRepository, roleRepository, new BCryptPasswordEncoder(), null, null, null, email, password, false);
    }

    @Test
    void isActiveOnEveryProfileExceptTest() {
        var profile = AdminBootstrapRunner.class.getAnnotation(Profile.class);

        assertNotNull(profile);
        assertArrayEquals(new String[]{"!test"}, profile.value());
    }

    @Test
    void whenCredentialsAreOptional_andNoneAreSet_leavesTheSeededDevAdminAlone() {
        assertDoesNotThrow(() -> optionalRunnerWith("", "").run(null));
        assertDoesNotThrow(() -> optionalRunnerWith(null, null).run(null));

        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void whenCredentialsAreOptional_aHalfConfiguredAdminIsStillAnError() {
        var ex = assertThrows(IllegalStateException.class, () -> optionalRunnerWith("root@example.com", "").run(null));

        assertTrue(ex.getMessage().contains("ADMIN_PASSWORD"), ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void whenCredentialsAreOptional_aShortPasswordIsStillRefused() {
        var ex = assertThrows(IllegalStateException.class,
                () -> optionalRunnerWith("root@example.com", "elevenchars").run(null));

        assertTrue(ex.getMessage().contains("12"), ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void failsFast_whenAdminEmailMissing() {
        var ex = assertThrows(IllegalStateException.class, () -> runnerWith("", "a-long-enough-password").run(null));

        assertTrue(ex.getMessage().contains("ADMIN_EMAIL"), ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void failsFast_whenAdminEmailIsNull() {
        var ex = assertThrows(IllegalStateException.class, () -> runnerWith(null, "a-long-enough-password").run(null));

        assertTrue(ex.getMessage().contains("ADMIN_EMAIL"), ex.getMessage());
    }

    @Test
    void failsFast_whenAdminPasswordMissing() {
        var ex = assertThrows(IllegalStateException.class, () -> runnerWith("root@example.com", "  ").run(null));

        assertTrue(ex.getMessage().contains("ADMIN_PASSWORD"), ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void failsFast_whenAdminPasswordShorterThanTwelveCharacters() {
        var ex = assertThrows(IllegalStateException.class, () -> runnerWith("root@example.com", "elevenchars").run(null));

        assertTrue(ex.getMessage().contains("ADMIN_PASSWORD"), ex.getMessage());
        assertTrue(ex.getMessage().contains("12"), ex.getMessage());
        assertFalse(ex.getMessage().contains("elevenchars"), "the password must never be echoed");
        verifyNoInteractions(userRepository);
    }
}
