package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-transactional on purpose (the confirmation mail is sent after commit). The signed-in user asks for a
 * new address with their current password; the address only replaces the old one once the link mailed to it
 * is opened, and until then the old address keeps working for sign-in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
class EmailChangeIntegrationTests {

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
        return "change-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private UserEntity user(String email) {
        return userService.saveUser(UserEntity.builder().firstname("Jane").lastname("Doe").email(email)
                .password(PASSWORD).role(studentRole).emailVerified(true).build(), passwordEncoder);
    }

    private String bearer(String email) throws Exception {
        var body = mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("accessToken").asText();
    }

    private void loginFails(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder requestChange(int userId, String auth, String newEmail, String password, String lang) {
        return post("/api/v1/users/" + userId + "/email-change").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"" + password + "\""
                        + (lang == null ? "" : ",\"lang\":\"" + lang + "\"") + "}");
    }

    private MockHttpServletRequestBuilder confirm(String token) {
        return post("/api/v1/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }

    private void pendingIs(int userId, String auth, String expected) throws Exception {
        var request = mockMvc.perform(get("/api/v1/users/" + userId + "/email-change").header("Authorization", auth))
                .andExpect(status().isOk());
        if (expected == null) {
            request.andExpect(jsonPath("$.pendingEmail").doesNotExist());
        } else {
            request.andExpect(jsonPath("$.pendingEmail").value(expected));
        }
    }

    @Test
    void passwordChangeCancelsPendingEmailAndResetLinks() throws Exception {
        var email = uniqueEmail();
        var user = user(email);
        var auth = bearer(email);
        mockMvc.perform(requestChange(user.getId(), auth, uniqueEmail(), PASSWORD, "fr"))
                .andExpect(status().isAccepted());
        var emailToken = CapturingEmailSender.extractToken(mailbox.last());
        mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("email", email))))
                .andExpect(status().isAccepted());
        var resetToken = CapturingEmailSender.extractToken(mailbox.last());
        mockMvc.perform(patch("/api/v1/users/" + user.getId()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("password", "Changed-pass-123", "currentPassword", PASSWORD))))
                .andExpect(status().isOk());
        mockMvc.perform(confirm(emailToken)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("token", resetToken,
                        "password", "Another-pass-123", "confirmedPassword", "Another-pass-123"))))
                .andExpect(status().isBadRequest());
    }

    // ---- requesting a change ----

    @Test
    void requestingAChangeMailsTheNewAddressOnlyAndLeavesTheAccountUntouched() throws Exception {
        var oldEmail = uniqueEmail();
        var newEmail = uniqueEmail();
        var user = user(oldEmail);
        var auth = bearer(oldEmail);

        mockMvc.perform(requestChange(user.getId(), auth, newEmail, PASSWORD, null))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        assertThat(mailbox.all()).hasSize(1);
        var message = mailbox.last();
        assertThat(message.to()).isEqualTo(newEmail);
        assertThat(message.text()).contains("http://localhost:4200/fr/verify-email#token=").contains("&type=change");
        assertThat(CapturingEmailSender.extractToken(message)).hasSize(43);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(oldEmail);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        bearer(oldEmail); // the old address still signs in
        loginFails(newEmail); // the new one does not, yet
        pendingIs(user.getId(), auth, newEmail);
    }

    @Test
    void theEmailIsInTheLanguageAskedFor() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);

        mockMvc.perform(requestChange(user.getId(), bearer(oldEmail), uniqueEmail(), PASSWORD, "en"))
                .andExpect(status().isAccepted());

        assertThat(mailbox.last().text()).contains("http://localhost:4200/en/verify-email#token=");
    }

    @Test
    void aWrongCurrentPasswordIsRefusedAndNothingIsSent() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);
        var auth = bearer(oldEmail);

        mockMvc.perform(requestChange(user.getId(), auth, uniqueEmail(), "not-the-password", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.currentPassword").exists());

        assertThat(mailbox.all()).isEmpty();
        assertThat(userTokenRepository.findAll()).isEmpty();
        pendingIs(user.getId(), auth, null);
    }

    @Test
    void anAddressAlreadyUsedByAnotherAccountIsRefusedWhateverItsCase() throws Exception {
        var oldEmail = uniqueEmail();
        var taken = uniqueEmail();
        var user = user(oldEmail);
        user(taken);

        mockMvc.perform(requestChange(user.getId(), bearer(oldEmail), taken.toUpperCase(), PASSWORD, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.newEmail").value("Cet email est déjà utilisé"));

        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void theCurrentAddressIsNotAChange() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);

        mockMvc.perform(requestChange(user.getId(), bearer(oldEmail), oldEmail, PASSWORD, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.newEmail").exists());

        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void aMalformedAddressIsRefused() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);

        mockMvc.perform(requestChange(user.getId(), bearer(oldEmail), "not-an-email", PASSWORD, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.newEmail").exists());
    }

    @Test
    void aSecondRequestReplacesTheFirstOneAndItsLinkDies() throws Exception {
        var oldEmail = uniqueEmail();
        var first = uniqueEmail();
        var second = uniqueEmail();
        var user = user(oldEmail);
        var auth = bearer(oldEmail);

        mockMvc.perform(requestChange(user.getId(), auth, first, PASSWORD, null)).andExpect(status().isAccepted());
        var firstToken = CapturingEmailSender.extractToken(mailbox.last());
        mockMvc.perform(requestChange(user.getId(), auth, second, PASSWORD, null)).andExpect(status().isAccepted());
        var secondToken = CapturingEmailSender.extractToken(mailbox.last());

        pendingIs(user.getId(), auth, second);
        mockMvc.perform(confirm(firstToken)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
        mockMvc.perform(confirm(secondToken)).andExpect(status().isNoContent());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(second);
    }

    // ---- confirming ----

    @Test
    void openingTheLinkSwapsTheAddressVerifiedAndTheOldOneStopsWorking() throws Exception {
        var oldEmail = uniqueEmail();
        var newEmail = uniqueEmail();
        var user = user(oldEmail);
        var auth = bearer(oldEmail);
        mockMvc.perform(requestChange(user.getId(), auth, newEmail, PASSWORD, null)).andExpect(status().isAccepted());
        var token = CapturingEmailSender.extractToken(mailbox.last());

        mockMvc.perform(confirm(token)).andExpect(status().isNoContent());

        var saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(newEmail);
        assertThat(saved.isEmailVerified()).isTrue();
        bearer(newEmail);
        loginFails(oldEmail);
        pendingIs(user.getId(), bearer(newEmail), null);
    }

    @Test
    void theLinkWorksOnlyOnce() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);
        mockMvc.perform(requestChange(user.getId(), bearer(oldEmail), uniqueEmail(), PASSWORD, null)).andExpect(status().isAccepted());
        var token = CapturingEmailSender.extractToken(mailbox.last());

        mockMvc.perform(confirm(token)).andExpect(status().isNoContent());
        mockMvc.perform(confirm(token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void anUnknownTokenIsRefused() throws Exception {
        mockMvc.perform(confirm("A".repeat(43))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void aVerificationTokenCannotConfirmAnEmailChange() throws Exception {
        var email = uniqueEmail();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"A\",\"lastname\":\"B\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"confirmedPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        var verificationToken = CapturingEmailSender.extractToken(mailbox.last());

        mockMvc.perform(confirm(verificationToken)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));
    }

    @Test
    void ifSomeoneElseTookTheAddressMeanwhileTheLinkIsRefusedAndNothingChanges() throws Exception {
        var oldEmail = uniqueEmail();
        var newEmail = uniqueEmail();
        var user = user(oldEmail);
        mockMvc.perform(requestChange(user.getId(), bearer(oldEmail), newEmail, PASSWORD, null)).andExpect(status().isAccepted());
        var token = CapturingEmailSender.extractToken(mailbox.last());
        user(newEmail);

        mockMvc.perform(confirm(token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.token").value(INVALID));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(oldEmail);
    }

    @Test
    void theSwapKillsAnOpenVerificationLinkForTheOldAddress() throws Exception {
        var oldEmail = uniqueEmail();
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"A\",\"lastname\":\"B\",\"email\":\"" + oldEmail + "\",\"password\":\"" + PASSWORD
                                + "\",\"confirmedPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        var verificationToken = CapturingEmailSender.extractToken(mailbox.last());
        var user = userRepository.findByEmailIgnoreCase(oldEmail).orElseThrow();
        mockMvc.perform(requestChange(user.getId(), bearer(oldEmail), uniqueEmail(), PASSWORD, null)).andExpect(status().isAccepted());
        mockMvc.perform(confirm(CapturingEmailSender.extractToken(mailbox.last()))).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + verificationToken + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theDailyLimitOfRequestsIsEnforcedPerAccount() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);
        var auth = bearer(oldEmail);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(requestChange(user.getId(), auth, uniqueEmail(), PASSWORD, null)).andExpect(status().isAccepted());
        }
        mockMvc.perform(requestChange(user.getId(), auth, uniqueEmail(), PASSWORD, null))
                .andExpect(status().isTooManyRequests());

        assertThat(mailbox.all()).hasSize(5);
    }

    // ---- cancelling and resending ----

    @Test
    void cancellingDropsThePendingAddressAndKillsItsLink() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);
        var auth = bearer(oldEmail);
        mockMvc.perform(requestChange(user.getId(), auth, uniqueEmail(), PASSWORD, null)).andExpect(status().isAccepted());
        var token = CapturingEmailSender.extractToken(mailbox.last());

        mockMvc.perform(delete("/api/v1/users/" + user.getId() + "/email-change").header("Authorization", auth))
                .andExpect(status().isNoContent());

        pendingIs(user.getId(), auth, null);
        mockMvc.perform(confirm(token)).andExpect(status().isBadRequest());
    }

    @Test
    void resendingRightAfterTheRequestHitsTheCooldown() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);
        var auth = bearer(oldEmail);
        mockMvc.perform(requestChange(user.getId(), auth, uniqueEmail(), PASSWORD, null)).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/users/" + user.getId() + "/email-change/resend").header("Authorization", auth))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void resendingWithNothingPendingIsAConflict() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);

        mockMvc.perform(post("/api/v1/users/" + user.getId() + "/email-change/resend").header("Authorization", bearer(oldEmail)))
                .andExpect(status().isConflict());
    }

    // ---- access ----

    @Test
    void onlyTheOwnerMayUseTheEndpoints() throws Exception {
        var ownerEmail = uniqueEmail();
        var otherEmail = uniqueEmail();
        var owner = user(ownerEmail);
        user(otherEmail);
        var otherAuth = bearer(otherEmail);

        mockMvc.perform(requestChange(owner.getId(), otherAuth, uniqueEmail(), PASSWORD, null)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/" + owner.getId() + "/email-change").header("Authorization", otherAuth)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/" + owner.getId() + "/email-change").header("Authorization", otherAuth)).andExpect(status().isForbidden());
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void anonymousCallersAreRefusedExceptForTheConfirmationLink() throws Exception {
        var user = user(uniqueEmail());

        mockMvc.perform(post("/api/v1/users/" + user.getId() + "/email-change").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"x@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/" + user.getId() + "/email-change")).andExpect(status().isUnauthorized());
    }

    // ---- the profile update no longer changes the address ----

    @Test
    void theProfileUpdateRefusesADifferentAddressAndPointsToTheEmailChangeFlow() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);

        mockMvc.perform(patch("/api/v1/users/" + user.getId()).header("Authorization", bearer(oldEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(oldEmail);
    }

    @Test
    void theProfileUpdateAcceptsTheUnchangedAddressWhateverItsCase() throws Exception {
        var oldEmail = uniqueEmail();
        var user = user(oldEmail);

        mockMvc.perform(patch("/api/v1/users/" + user.getId()).header("Authorization", bearer(oldEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Janet\",\"email\":\"" + oldEmail.toUpperCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstname").value("Janet"))
                .andExpect(jsonPath("$.email").value(oldEmail));
    }
}
