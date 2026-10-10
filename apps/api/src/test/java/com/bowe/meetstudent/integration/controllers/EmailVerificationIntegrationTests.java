package com.bowe.meetstudent.integration.controllers;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.UserToken;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-transactional on purpose: the verification mail is sent by an AFTER_COMMIT listener, which never
 * fires inside a rolled-back test transaction. Each test creates users with unique addresses and
 * {@link #cleanUp()} removes everything they created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
class EmailVerificationIntegrationTests {

    private static final String PASSWORD = "S3cure-test-pass";
    private static final String INVALID = "Lien invalide ou expiré";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CapturingEmailSender mailbox;
    @Autowired private UserRepository userRepository;
    @Autowired private UserTokenRepository userTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Role studentRole;

    @BeforeEach
    void setUp() {
        mailbox.clear();
        studentRole = roleRepository.findByName("ROLE_STUDENT")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_STUDENT").description("Student").build()));
    }

    @AfterEach
    void cleanUp() {
        userTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---- helpers ----

    private static String uniqueEmail() {
        return "verify-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String registrationJson(String email, String lang) {
        return "{\"firstname\":\"Jane\",\"lastname\":\"Doe\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"confirmedPassword\":\"" + PASSWORD + "\"" + (lang == null ? "" : ",\"lang\":\"" + lang + "\"") + "}";
    }

    private JsonNode register(String email, String lang) throws Exception {
        var body = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(email, lang)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String login(String email) throws Exception {
        var body = mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder confirm(String token) {
        return post("/api/v1/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }

    private UserEntity directUser(String email) {
        return userService.saveUser(UserEntity.builder().firstname("Direct").lastname("User").email(email)
                .password(PASSWORD).role(studentRole).build(), passwordEncoder);
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    // ---- registration ----

    @Test
    void registrationAnswers201UnverifiedAndSendsOneMailWithAFragmentLink() throws Exception {
        var email = uniqueEmail();

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(email, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false));

        assertThat(mailbox.all()).hasSize(1);
        var message = mailbox.last();
        assertThat(message.to()).isEqualTo(email);
        assertThat(message.text()).contains("http://localhost:4200/fr/verify-email#token=");
        var token = CapturingEmailSender.extractToken(message);
        assertThat(token).hasSize(43);

        var rows = userTokenRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTokenHash()).hasSize(64).isNotEqualTo(token).isEqualTo(sha256Hex(token));
        assertThat(rows.get(0).getEmail()).isEqualTo(email);
    }

    @Test
    void registrationWithEnglishLanguageSendsAnEnglishLink() throws Exception {
        register(uniqueEmail(), "en");

        assertThat(mailbox.last().text()).contains("http://localhost:4200/en/verify-email#token=");
    }

    @Test
    void registrationRejectsAnUnsupportedLanguage() throws Exception {
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(uniqueEmail(), "de")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.lang").exists());
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void aHostileHostHeaderNeverChangesTheLinkBase() throws Exception {
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .header("Host", "evil.example")
                        .header("X-Forwarded-Host", "evil.example")
                        .header("X-Forwarded-Proto", "https")
                        .content(registrationJson(uniqueEmail(), null)))
                .andExpect(status().isCreated());

        var text = mailbox.last().text();
        assertThat(text).contains("http://localhost:4200/fr/verify-email#token=").doesNotContain("evil.example");
        assertThat(mailbox.last().html()).doesNotContain("evil.example");
    }

    // ---- confirm ----

    @Test
    void confirmingMarksTheUserVerifiedAndLoginKeepsWorking() throws Exception {
        var email = uniqueEmail();
        var user = register(email, null);
        var token = CapturingEmailSender.extractToken(mailbox.last());
        login(email); // unverified accounts can sign in

        mockMvc.perform(confirm(token)).andExpect(status().isNoContent());

        var accessToken = login(email);
        mockMvc.perform(get("/api/v1/users/id/" + user.get("id").asInt()).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        register(uniqueEmail(), null);
        var token = CapturingEmailSender.extractToken(mailbox.last());
        mockMvc.perform(confirm(token)).andExpect(status().isNoContent());

        mockMvc.perform(confirm(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void unknownExpiredAndConsumedTokensAllGetTheIdenticalBody() throws Exception {
        register(uniqueEmail(), null);
        var token = CapturingEmailSender.extractToken(mailbox.last());

        var unknown = mockMvc.perform(confirm("A".repeat(43))).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        UserToken row = userTokenRepository.findAll().get(0);
        row.setExpiresAt(Instant.now().minusSeconds(1));
        userTokenRepository.save(row);
        var expired = mockMvc.perform(confirm(token)).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        row.setExpiresAt(Instant.now().plusSeconds(3600));
        row.setConsumedAt(Instant.now());
        userTokenRepository.save(row);
        var consumed = mockMvc.perform(confirm(token)).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo("{\"token\":\"" + INVALID + "\"}");
        assertThat(expired).isEqualTo(unknown);
        assertThat(consumed).isEqualTo(unknown);
    }

    @Test
    void blankAndOversizedTokensAreRejectedWithTheSameMessage() throws Exception {
        mockMvc.perform(confirm("")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        mockMvc.perform(confirm("x".repeat(129))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    // ---- resend ----

    @Test
    void resendAnonymousIs401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend"))
                .andExpect(status().isUnauthorized());
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void resendIs202ThenTheImmediateSecondIs429WithRetryAfter() throws Exception {
        var user = directUser(uniqueEmail());
        var auth = TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT");

        mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(auth))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
        assertThat(mailbox.all()).hasSize(1);
        assertThat(mailbox.last().to()).isEqualTo(user.getEmail());

        mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(auth))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
        assertThat(mailbox.all()).hasSize(1);
    }

    @Test
    void theRetryAfterHeaderIsBetweenOneAndSixtySeconds() throws Exception {
        var user = directUser(uniqueEmail());
        var auth = TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT");
        mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(auth)).andExpect(status().isAccepted());

        var retryAfter = mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(auth))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");

        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    @Test
    void aNewResendInvalidatesTheLinkOfTheEarlierMail() throws Exception {
        var user = directUser(uniqueEmail());
        var auth = TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT");
        mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(auth)).andExpect(status().isAccepted());
        var first = CapturingEmailSender.extractToken(mailbox.last());
        // Let the cooldown pass without waiting: age the first row.
        var row = userTokenRepository.findAll().get(0);
        row.setIssuedAt(Instant.now().minusSeconds(120));
        userTokenRepository.save(row);

        mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(auth)).andExpect(status().isAccepted());
        var second = CapturingEmailSender.extractToken(mailbox.last());

        assertThat(second).isNotEqualTo(first);
        mockMvc.perform(confirm(first)).andExpect(status().isBadRequest());
        mockMvc.perform(confirm(second)).andExpect(status().isNoContent());
    }

    @Test
    void resendByAVerifiedUserIs409() throws Exception {
        var user = directUser(uniqueEmail());
        user.setEmailVerified(true);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/email-verification/resend").with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(mailbox.all()).isEmpty();
    }

    // ---- email change ----

    @Test
    void changingTheEmailResetsVerificationKillsTheOldLinkAndMailsTheNewAddress() throws Exception {
        var oldEmail = uniqueEmail();
        var newEmail = uniqueEmail();
        var user = register(oldEmail, null);
        var oldToken = CapturingEmailSender.extractToken(mailbox.last());
        var accessToken = login(oldEmail);
        mailbox.clear();

        mockMvc.perform(put("/api/v1/users/" + user.get("id").asInt())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.email").value(newEmail));

        mockMvc.perform(confirm(oldToken)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        assertThat(mailbox.all()).hasSize(1);
        assertThat(mailbox.last().to()).isEqualTo(newEmail);
        var newToken = CapturingEmailSender.extractToken(mailbox.last());
        assertThat(newToken).isNotEqualTo(oldToken);
        mockMvc.perform(confirm(newToken)).andExpect(status().isNoContent());
        assertThat(userRepository.findByEmailIgnoreCase(newEmail).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void changingTheEmailOfAVerifiedUserMakesHimUnverifiedAgain() throws Exception {
        var oldEmail = uniqueEmail();
        var user = register(oldEmail, null);
        mockMvc.perform(confirm(CapturingEmailSender.extractToken(mailbox.last()))).andExpect(status().isNoContent());
        var accessToken = login(oldEmail);

        mockMvc.perform(put("/api/v1/users/" + user.get("id").asInt())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void updatingOtherProfileFieldsKeepsVerification() throws Exception {
        var email = uniqueEmail();
        var user = register(email, null);
        mockMvc.perform(confirm(CapturingEmailSender.extractToken(mailbox.last()))).andExpect(status().isNoContent());
        var accessToken = login(email);
        mailbox.clear();

        mockMvc.perform(put("/api/v1/users/" + user.get("id").asInt())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Janet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(mailbox.all()).isEmpty();
    }

    // ---- logging ----

    @Test
    void logsNeverContainTheTokenOrItsHash() throws Exception {
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            LoggerFactory.getLogger("test.sentinel").error("sentinel");
            register(uniqueEmail(), null);
            var token = CapturingEmailSender.extractToken(mailbox.last());
            mockMvc.perform(confirm(token)).andExpect(status().isNoContent());
            mockMvc.perform(confirm(token)).andExpect(status().isBadRequest());

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).contains("sentinel");
            var hash = sha256Hex(token);
            for (var event : appender.list) {
                assertThat(event.getFormattedMessage()).doesNotContain(token).doesNotContain(hash);
                if (event.getThrowableProxy() != null) {
                    assertThat(event.getThrowableProxy().getMessage() == null ? "" : event.getThrowableProxy().getMessage())
                            .doesNotContain(token);
                }
            }
        } finally {
            root.detachAppender(appender);
        }
    }
}
