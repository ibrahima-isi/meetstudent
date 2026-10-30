package com.bowe.meetstudent.support;

import com.bowe.meetstudent.services.ContactRateLimiter;
import com.bowe.meetstudent.services.CredentialAttemptLimiter;
import com.bowe.meetstudent.services.ForumRateLimiter;
import com.bowe.meetstudent.services.TagService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The second test must find the shared context as a fresh one would be, whatever the first one left behind. */
@SpringBootTest
@ExtendWith(ResetStateExtension.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResetStateExtensionIntegrationTests {

    @Autowired
    private TagService tagService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CredentialAttemptLimiter credentialAttemptLimiter;
    @Autowired
    private ForumRateLimiter forumRateLimiter;
    @Autowired
    private ContactRateLimiter contactRateLimiter;

    @Test
    @Order(1)
    void leavesRowsAndLimiterEntriesBehind() {
        tagService.findOrCreate("FIRST");
        tagService.findOrCreate("SECOND");
        assertThatThrownBy(() -> credentialAttemptLimiter.authenticate("someone@example.com",
                () -> { throw new BadCredentialsException("wrong password"); }))
                .isInstanceOf(BadCredentialsException.class);
        forumRateLimiter.check(1);
        contactRateLimiter.check("203.0.113.7");

        assertThat(jdbc.queryForObject("select count(*) from tags", Integer.class)).isEqualTo(2);
        assertThat(credentialAttemptLimiter.trackedKeys()).isPositive();
        assertThat(forumRateLimiter.trackedUsers()).isPositive();
        assertThat(contactRateLimiter.trackedClients()).isPositive();
    }

    @Test
    @Order(2)
    void startsFromEmptyTablesRestartedIdsAndForgottenLimiters() {
        assertThat(jdbc.queryForObject("select count(*) from tags", Integer.class)).isZero();
        assertThat(tagService.findOrCreate("THIRD").getId()).isEqualTo(1);
        assertThat(credentialAttemptLimiter.trackedKeys()).isZero();
        assertThat(forumRateLimiter.trackedUsers()).isZero();
        assertThat(contactRateLimiter.trackedClients()).isZero();
    }
}
