package com.bowe.meetstudent.integration.controllers;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import com.bowe.meetstudent.services.UserTokenService;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-transactional on purpose: the reset mail is sent by an AFTER_COMMIT listener, which never fires
 * inside a rolled-back test transaction. {@link #cleanUp()} removes everything the tests created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
class PasswordResetIntegrationTests {

    private static final String OLD_PASSWORD = "Old-secret-pass";
    private static final String NEW_PASSWORD = "New-secret-pass";
    private static final String INVALID = "Lien invalide ou expiré";
    private static final String LINK = "http://localhost:4200/fr/reset-password#token=";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CapturingEmailSender mailbox;
    @Autowired private UserRepository userRepository;
    @Autowired private UserTokenRepository userTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserTokenService userTokenService;
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
        return "reset-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private UserEntity user(String email) {
        return userRepository.save(UserEntity.builder().firstname("Jane").lastname("Doe").email(email)
                .password(passwordEncoder.encode(OLD_PASSWORD)).role(studentRole).build());
    }

    private MockHttpServletRequestBuilder forgot(String email) {
        return post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}");
    }

    private MockHttpServletRequestBuilder reset(String token, String password, String confirmed) {
        return post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\",\"confirmedPassword\":\"" + confirmed + "\"}");
    }

    private MockHttpServletRequestBuilder reset(String token) {
        return reset(token, NEW_PASSWORD, NEW_PASSWORD);
    }

    private JsonNode login(String email, String password, int expectedStatus) throws Exception {
        var body = mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return expectedStatus == 200 ? objectMapper.readTree(body) : null;
    }

    private String tokenOfLastMail() {
        return CapturingEmailSender.extractToken(mailbox.last());
    }

    /** Lets the per-user cooldown pass without waiting. */
    private void ageTokens() {
        for (var row : userTokenRepository.findAll()) {
            row.setIssuedAt(row.getIssuedAt().minusSeconds(120));
            userTokenRepository.save(row);
        }
    }

    /** Status, every header but Date, and the body bytes, in a comparable form. */
    private Map<String, Object> fingerprint(MvcResult result) throws Exception {
        var response = result.getResponse();
        var headers = new TreeMap<String, List<String>>();
        for (var name : response.getHeaderNames()) {
            if (!"Date".equalsIgnoreCase(name)) {
                headers.put(name.toLowerCase(), response.getHeaders(name));
            }
        }
        return Map.of("status", response.getStatus(), "headers", headers, "body", response.getContentAsString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> askFor(String email) throws Exception {
        return fingerprint(mockMvc.perform(forgot(email)).andReturn());
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    // ---- request: no enumeration ----

    @Test
    void knownAndUnknownAddressesGetByteIdenticalAnswersButOnlyTheAccountGetsAMail() throws Exception {
        var email = uniqueEmail();
        user(email);

        var unknown = askFor(uniqueEmail());
        assertThat(mailbox.all()).isEmpty();
        var known = askFor(email);

        assertThat(known).isEqualTo(unknown);
        assertThat(known.get("status")).isEqualTo(202);
        assertThat(known.get("body")).isEqualTo("{\"status\":\"accepted\"}");
        assertThat(mailbox.all()).hasSize(1);
        var message = mailbox.last();
        assertThat(message.type()).isEqualTo(EmailType.PASSWORD_RESET);
        assertThat(message.to()).isEqualTo(email);
        assertThat(message.text()).contains(LINK);
        assertThat(tokenOfLastMail()).hasSize(43);
    }

    @Test
    void aCaseVariantOfTheAddressGetsTheSameAnswerAndTheMail() throws Exception {
        var email = "a-" + UUID.randomUUID().toString().substring(0, 8) + "@b.sn";
        user(email);

        var unknown = askFor(uniqueEmail());
        var variant = askFor(email.toUpperCase());

        assertThat(variant).isEqualTo(unknown);
        assertThat(mailbox.all()).hasSize(1);
        assertThat(mailbox.last().to()).isEqualTo(email);
    }

    @Test
    void caseVariantDuplicateAccountsDoNotBreakTheRequest() throws Exception {
        var email = "dup-" + UUID.randomUUID().toString().substring(0, 8) + "@b.sn";
        user(email);
        user(email.toUpperCase());

        var unknown = askFor(uniqueEmail());
        var duplicated = askFor(email);

        assertThat(duplicated).isEqualTo(unknown);
        assertThat(mailbox.all()).hasSize(1);
    }

    @Test
    void aCooledDownOrCappedRequestLooksLikeAnyOther() throws Exception {
        var email = uniqueEmail();
        user(email);
        var unknown = askFor(uniqueEmail());

        askFor(email);
        var cooledDown = askFor(email);

        assertThat(cooledDown).isEqualTo(unknown);
        assertThat(mailbox.all()).hasSize(1);

        // fill the per-address budget (3), then ask once more
        ageTokens();
        askFor(email);
        ageTokens();
        askFor(email);
        ageTokens();
        var capped = askFor(email);

        assertThat(capped).isEqualTo(unknown);
        assertThat(mailbox.all()).hasSize(3);
    }

    @Test
    void twoRequestsWithinTheCooldownSendOneMail() throws Exception {
        var email = uniqueEmail();
        user(email);

        mockMvc.perform(forgot(email)).andExpect(status().isAccepted());
        mockMvc.perform(forgot(email)).andExpect(status().isAccepted());

        assertThat(mailbox.all()).hasSize(1);
    }

    @Test
    void theAddressCapHoldsAcrossTwoAccountsSharingTheAddress() throws Exception {
        var email = "shared-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        var first = user(email);
        var second = user(email.toUpperCase());
        // two tokens issued to the other account for the same mailbox, one to the first account
        userTokenService.issue(second, UserTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));
        userTokenService.consumeOutstanding(second.getId(), UserTokenPurpose.PASSWORD_RESET);
        ageTokens();
        userTokenService.issue(second, UserTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));
        ageTokens();
        userTokenService.issue(first, UserTokenPurpose.PASSWORD_RESET, Duration.ofHours(1));
        ageTokens();

        mockMvc.perform(forgot(email)).andExpect(status().isAccepted());

        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void blankOrMalformedEmailIsA400OnTheEmailField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").exists());
        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").exists());
        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").exists());
    }

    @Test
    void theTokenLivesOneHourAndOnlyItsHashIsStored() throws Exception {
        var email = uniqueEmail();
        user(email);

        mockMvc.perform(forgot(email)).andExpect(status().isAccepted());

        var rows = userTokenRepository.findAll();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getPurpose()).isEqualTo(UserTokenPurpose.PASSWORD_RESET);
        assertThat(Duration.between(row.getIssuedAt(), row.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(row.getTokenHash()).isEqualTo(sha256Hex(tokenOfLastMail())).isNotEqualTo(tokenOfLastMail());
    }

    @Test
    void englishRequestsGetAnEnglishLink() throws Exception {
        var email = uniqueEmail();
        user(email);

        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"lang\":\"en\"}"))
                .andExpect(status().isAccepted());

        assertThat(mailbox.last().text()).contains("http://localhost:4200/en/reset-password#token=");
    }

    // ---- confirm ----

    @Test
    void endToEndTheResetKillsTheSessionAndSwitchesThePassword() throws Exception {
        var email = uniqueEmail();
        user(email);
        var session = login(email, OLD_PASSWORD, 200);
        var oldRefresh = session.get("refreshToken").asText();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        mockMvc.perform(forgot(email)).andExpect(status().isAccepted());
        mockMvc.perform(reset(tokenOfLastMail())).andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.count()).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        login(email, OLD_PASSWORD, 401);
        login(email, NEW_PASSWORD, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"})
    void resetRevokesEveryAccessAndRefreshCredentialForEveryRole(String roleName) throws Exception {
        var role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).build()));
        var account = user(uniqueEmail());
        account.setRole(role);
        userRepository.save(account);
        var first = login(account.getEmail(), OLD_PASSWORD, 200);
        var second = login(account.getEmail(), OLD_PASSWORD, 200);
        var unknown = askFor(uniqueEmail());
        assertThat(askFor(account.getEmail())).isEqualTo(unknown);
        assertThat(mailbox.last().to()).isEqualTo(account.getEmail());

        mockMvc.perform(reset(tokenOfLastMail())).andExpect(status().isNoContent());

        for (var session : List.of(first, second)) {
            mockMvc.perform(get("/api/v1/users/id/" + account.getId())
                            .header("Authorization", "Bearer " + session.get("accessToken").asText()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.get("refreshToken").asText()))))
                    .andExpect(status().isUnauthorized());
        }
        login(account.getEmail(), OLD_PASSWORD, 401);
        var fresh = login(account.getEmail(), NEW_PASSWORD, 200);
        mockMvc.perform(get("/api/v1/users/id/" + account.getId())
                        .header("Authorization", "Bearer " + fresh.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void resetCancelsPendingEmailChangeWithoutChangingTheAddress() throws Exception {
        var account = user(uniqueEmail());
        var session = login(account.getEmail(), OLD_PASSWORD, 200);
        mockMvc.perform(post("/api/v1/users/" + account.getId() + "/email-change")
                        .header("Authorization", "Bearer " + session.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newEmail", uniqueEmail(), "currentPassword", OLD_PASSWORD))))
                .andExpect(status().isAccepted());
        var emailLink = tokenOfLastMail();
        mockMvc.perform(forgot(account.getEmail())).andExpect(status().isAccepted());
        mockMvc.perform(reset(tokenOfLastMail())).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", emailLink))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        login(account.getEmail(), NEW_PASSWORD, 200);
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        var email = uniqueEmail();
        user(email);
        mockMvc.perform(forgot(email));
        var token = tokenOfLastMail();

        mockMvc.perform(reset(token)).andExpect(status().isNoContent());
        mockMvc.perform(reset(token, "Third-secret-pass", "Third-secret-pass"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));

        login(email, NEW_PASSWORD, 200);
    }

    @Test
    void aNewRequestDoesNotKillTheEarlierLinkButUsingOneKillsTheOther() throws Exception {
        var email = uniqueEmail();
        user(email);
        mockMvc.perform(forgot(email));
        var first = tokenOfLastMail();
        ageTokens();
        mockMvc.perform(forgot(email));
        var second = tokenOfLastMail();

        assertThat(second).isNotEqualTo(first);
        // an attacker spending the address budget must not be able to kill the owner's live link
        mockMvc.perform(reset(first)).andExpect(status().isNoContent());
        mockMvc.perform(reset(second)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void parallelRequestsForOneAddressCannotBypassTheLimits() throws Exception {
        var email = uniqueEmail();
        user(email);
        int threads = 12;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var ready = new java.util.concurrent.CountDownLatch(threads);
        var go = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(forgot(email)).andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            for (var f : futures) {
                assertThat(f.get(60, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(202);
            }
        } finally {
            pool.shutdownNow();
        }

        // cooldown alone allows exactly one mail; the address cap (3) is the hard ceiling
        assertThat(mailbox.all()).hasSize(1);
        assertThat(userTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void aPasswordOver72BytesIsA400OnPasswordAndDoesNotBurnTheToken() throws Exception {
        var email = uniqueEmail();
        user(email);
        mockMvc.perform(forgot(email));
        var token = tokenOfLastMail();
        var twoByte = "é".repeat(40);

        mockMvc.perform(reset(token, twoByte, twoByte))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value("Le mot de passe ne doit pas dépasser 72 octets"));
        mockMvc.perform(reset(token, "a".repeat(73), "a".repeat(73)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.password").exists());

        var max = "a".repeat(72);
        mockMvc.perform(reset(token, max, max)).andExpect(status().isNoContent());
        login(email, max, 200);
    }

    @Test
    void registrationAndProfileUpdateRejectPasswordsOver72Bytes() throws Exception {
        var twoByte = "é".repeat(40);
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"A\",\"lastname\":\"B\",\"email\":\"" + uniqueEmail() + "\",\"password\":\"" + twoByte
                                + "\",\"confirmedPassword\":\"" + twoByte + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value("Le mot de passe ne doit pas dépasser 72 octets"));

        var email = uniqueEmail();
        var account = user(email);
        var access = login(email, OLD_PASSWORD, 200).get("accessToken").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/" + account.getId())
                        .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + twoByte + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value("Le mot de passe ne doit pas dépasser 72 octets"));
    }

    @Test
    void changingThePasswordFromTheProfileKillsOutstandingResetLinks() throws Exception {
        var email = uniqueEmail();
        var account = user(email);
        mockMvc.perform(forgot(email));
        var token = tokenOfLastMail();
        var access = login(email, OLD_PASSWORD, 200).get("accessToken").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/" + account.getId())
                        .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("password", "Profile-new-pass1", "currentPassword", OLD_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(reset(token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void unknownBlankAndOversizedTokensAreAllInvalid() throws Exception {
        mockMvc.perform(reset("nope")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        mockMvc.perform(reset("")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        mockMvc.perform(reset("x".repeat(500))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void aShortPasswordIsA400OnPasswordAndDoesNotBurnTheToken() throws Exception {
        var email = uniqueEmail();
        user(email);
        mockMvc.perform(forgot(email));
        var token = tokenOfLastMail();

        mockMvc.perform(reset(token, "1234567", "1234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value("Le mot de passe doit contenir au moins 8 caractères"));

        mockMvc.perform(reset(token)).andExpect(status().isNoContent());
    }

    @Test
    void aMismatchIsA400OnConfirmedPasswordAndDoesNotBurnTheToken() throws Exception {
        var email = uniqueEmail();
        user(email);
        mockMvc.perform(forgot(email));
        var token = tokenOfLastMail();

        mockMvc.perform(reset(token, NEW_PASSWORD, "Different-pass-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.confirmedPassword").value("Les mots de passe ne correspondent pas"));

        mockMvc.perform(reset(token)).andExpect(status().isNoContent());
        login(email, OLD_PASSWORD, 401);
    }

    @Test
    void aMissingPasswordIsA400OnPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"confirmedPassword\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.password").exists());
    }

    @Test
    void theLinkDiesWhenTheAddressChangedSinceTheRequest() throws Exception {
        var email = uniqueEmail();
        var account = user(email);
        mockMvc.perform(forgot(email));
        var token = tokenOfLastMail();
        account.setEmail(uniqueEmail());
        userRepository.save(account);

        mockMvc.perform(reset(token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        login(account.getEmail(), OLD_PASSWORD, 200);
    }

    @Test
    void anExpiredLinkIsInvalid() throws Exception {
        var email = uniqueEmail();
        user(email);
        mockMvc.perform(forgot(email));
        var token = tokenOfLastMail();
        for (var row : userTokenRepository.findAll()) {
            row.setExpiresAt(Instant.now().minusSeconds(5));
            userTokenRepository.save(row);
        }

        mockMvc.perform(reset(token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void resettingProvesTheMailboxSoTheAddressBecomesVerified() throws Exception {
        var email = uniqueEmail();
        var account = user(email);
        assertThat(account.isEmailVerified()).isFalse();
        mockMvc.perform(forgot(email));

        mockMvc.perform(reset(tokenOfLastMail())).andExpect(status().isNoContent());

        assertThat(userRepository.findById(account.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    // ---- purpose isolation ----

    @Test
    void aResetTokenCannotVerifyAnEmailAndAVerificationTokenCannotResetAPassword() throws Exception {
        var email = uniqueEmail();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Jane\",\"lastname\":\"Doe\",\"email\":\"" + email + "\",\"password\":\"" + OLD_PASSWORD
                                + "\",\"confirmedPassword\":\"" + OLD_PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(forgot(email)).andExpect(status().isAccepted());
        var mails = mailbox.all();
        assertThat(mails).hasSize(2);
        var verification = CapturingEmailSender.extractToken(mails.stream().filter(m -> m.type() == EmailType.EMAIL_VERIFICATION).findFirst().orElseThrow());
        var resetToken = CapturingEmailSender.extractToken(mails.stream().filter(m -> m.type() == EmailType.PASSWORD_RESET).findFirst().orElseThrow());

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        mockMvc.perform(reset(verification)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));

        // both still usable on their own endpoint
        mockMvc.perform(reset(resetToken)).andExpect(status().isNoContent());
    }

    // ---- logging ----

    @Test
    void logsNeverContainTheTokenItsHashOrTheAddress() throws Exception {
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            LoggerFactory.getLogger("test.sentinel").error("sentinel");
            var email = uniqueEmail();
            user(email);
            mockMvc.perform(forgot(email)).andExpect(status().isAccepted());
            mockMvc.perform(forgot(email)).andExpect(status().isAccepted()); // cooldown path
            mockMvc.perform(forgot(uniqueEmail())).andExpect(status().isAccepted());
            var token = tokenOfLastMail();
            mockMvc.perform(reset(token)).andExpect(status().isNoContent());
            mockMvc.perform(reset(token)).andExpect(status().isBadRequest());

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).contains("sentinel");
            var hash = sha256Hex(token);
            for (var event : appender.list) {
                var text = event.getFormattedMessage();
                assertThat(text).doesNotContain(token).doesNotContain(hash).doesNotContain(email).doesNotContain(NEW_PASSWORD);
                if (event.getThrowableProxy() != null && event.getThrowableProxy().getMessage() != null) {
                    assertThat(event.getThrowableProxy().getMessage()).doesNotContain(token).doesNotContain(email);
                }
            }
        } finally {
            root.detachAppender(appender);
        }
    }
}
