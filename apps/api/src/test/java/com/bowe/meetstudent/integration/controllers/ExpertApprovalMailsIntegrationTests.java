package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.ExpertEmailRequested;
import com.bowe.meetstudent.mail.SendOutcome;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mails of the Expert approval flow: request received (registration), approved, rejected (with the escaped reason).
 * Non-transactional: the mails leave from AFTER_COMMIT listeners (synchronous executor in the test profile).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
class ExpertApprovalMailsIntegrationTests {

    private static final String URL = "/api/v1/admin/experts";

    @Autowired MockMvc mvc;
    @Autowired CapturingEmailSender mailbox;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired PasswordEncoder encoder;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        mailbox.clear();
        role("ROLE_STUDENT");
        role("ROLE_EXPERT");
    }

    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> roles.save(Role.builder().name(name).build()));
    }

    private UserEntity expert(String email, ExpertStatus status) {
        return userService.saveUser(UserEntity.builder().firstname("Awa").lastname("Diop").email(email)
                .qualification("Maths").password("password").role(role("ROLE_EXPERT")).expertStatus(status).build(), encoder);
    }

    private static String register(String roleName, String email, String firstname, String lang) {
        return "{\"firstname\":\"" + firstname + "\",\"lastname\":\"Diop\",\"email\":\"" + email
                + "\",\"password\":\"password109\",\"confirmedPassword\":\"password109\",\"role\":\"" + roleName
                + "\",\"qualification\":\"Maths\"" + (lang == null ? "" : ",\"lang\":\"" + lang + "\"") + "}";
    }

    private void registerAs(String roleName, String email, String firstname, String lang) throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(register(roleName, email, firstname, lang))).andExpect(status().isCreated());
    }

    private void approve(int id, String query) throws Exception {
        mvc.perform(post(URL + "/" + id + "/approve" + query).with(TestDataUtil.mockUser(9999, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    private void reject(int id, String query, String json) throws Exception {
        mvc.perform(post(URL + "/" + id + "/reject" + query).with(TestDataUtil.mockUser(9999, "ROLE_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    private long countOf(EmailType type) {
        return mailbox.all().stream().filter(m -> m.type() == type).count();
    }

    @Test
    void teacherRegistrationSendsTheRequestReceivedMailToThatAddressOnly() throws Exception {
        registerAs("ROLE_EXPERT", "teacher@example.com", "Awa", "en");

        var received = mailbox.all().stream().filter(m -> m.type() == EmailType.EXPERT_REQUEST_RECEIVED).toList();
        assertThat(received).hasSize(1);
        var mail = received.get(0);
        assertThat(mail.to()).isEqualTo("teacher@example.com");
        assertThat(mail.subject()).contains("MeetStudent");
        assertThat(mail.text()).contains("Hello Awa").contains("pending");
        assertThat(mail.html()).contains("Awa");
        assertThat(mail.text()).doesNotContain("password109").doesNotContain("token=");
        assertThat(mailbox.all()).allMatch(m -> m.to().equals("teacher@example.com"));
    }

    @Test
    void studentRegistrationSendsNoExpertMail() throws Exception {
        registerAs("ROLE_STUDENT", "student@example.com", "Moussa", null);
        assertThat(countOf(EmailType.EXPERT_REQUEST_RECEIVED)).isZero();
    }

    @Test
    void registrationStillSucceedsWhenTheMailCannotBeSent() throws Exception {
        mailbox.respondWith(SendOutcome.CONNECTION_FAILED);
        registerAs("ROLE_EXPERT", "down@example.com", "Awa", "fr");
        assertThat(users.findByEmailIgnoreCase("down@example.com").orElseThrow().getExpertStatus())
                .isEqualTo(ExpertStatus.PENDING);
    }

    @Test
    void approvalSendsOneMailToTheExpertInTheRequestedLanguage() throws Exception {
        var e = expert("ok@example.com", ExpertStatus.PENDING);
        approve(e.getId(), "?lang=en");

        var approved = mailbox.all().stream().filter(m -> m.type() == EmailType.EXPERT_APPROVED).toList();
        assertThat(approved).hasSize(1);
        assertThat(approved.get(0).to()).isEqualTo("ok@example.com");
        assertThat(approved.get(0).text()).contains("approved");
        assertThat(mailbox.all()).hasSize(1);
        assertThat(users.findById(e.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.APPROVED);
    }

    @Test
    void approvalMailDefaultsToFrench() throws Exception {
        var e = expert("fr@example.com", ExpertStatus.PENDING);
        approve(e.getId(), "");
        assertThat(mailbox.last().text()).contains("Bonjour Awa").contains("approuv");
    }

    @Test
    void rejectionMailCarriesTheReasonEscapedInHtmlAndAsPlainTextWithoutHeaderInjection() throws Exception {
        var e = expert("no@example.com", ExpertStatus.PENDING);
        reject(e.getId(), "?lang=fr", "{\"reason\":\"<script>alert(1)</script> & \\\"x\\\"\\r\\nBcc: evil@example.com\"}");

        var mail = mailbox.last();
        assertThat(mail.type()).isEqualTo(EmailType.EXPERT_REJECTED);
        assertThat(mail.to()).isEqualTo("no@example.com");
        assertThat(mail.html()).doesNotContain("<script>").contains("&lt;script&gt;").contains("&amp;");
        assertThat(mail.text()).contains("<script>alert(1)</script>");
        assertThat(mail.replyTo()).isNull();
        assertThat(mailbox.all()).allMatch(m -> m.to().equals("no@example.com"));
    }

    @Test
    void rejectionWithoutReasonMentionsNoReason() throws Exception {
        var e = expert("nr@example.com", ExpertStatus.PENDING);
        reject(e.getId(), "?lang=en", "{}");
        assertThat(mailbox.last().type()).isEqualTo(EmailType.EXPERT_REJECTED);
        assertThat(mailbox.last().text()).doesNotContain("Reason:").doesNotContain("null");
    }

    @Test
    void aFailingSendNeverBlocksTheDecision() throws Exception {
        mailbox.respondWith(SendOutcome.AUTH_FAILED);
        var e = expert("fail@example.com", ExpertStatus.PENDING);
        mvc.perform(post(URL + "/" + e.getId() + "/approve").with(TestDataUtil.mockUser(9999, "ROLE_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(users.findById(e.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.APPROVED);
    }

    @Test
    void repeatingTheSameDecisionIsANoOpWithoutMailRedateOrReasonOverwrite() throws Exception {
        var e = expert("idem@example.com", ExpertStatus.PENDING);
        approve(e.getId(), "");
        var firstDate = users.findById(e.getId()).orElseThrow().getExpertDecisionDate();
        approve(e.getId(), "");
        var afterApprove = users.findById(e.getId()).orElseThrow();
        assertThat(afterApprove.getExpertDecisionDate()).isEqualTo(firstDate);
        assertThat(countOf(EmailType.EXPERT_APPROVED)).isEqualTo(1);

        reject(e.getId(), "", "{\"reason\":\"premier motif\"}");
        var rejected = users.findById(e.getId()).orElseThrow();
        reject(e.getId(), "", "{\"reason\":\"autre motif\"}");
        var again = users.findById(e.getId()).orElseThrow();
        assertThat(again.getExpertStatusReason()).isEqualTo("premier motif");
        assertThat(again.getExpertDecisionDate()).isEqualTo(rejected.getExpertDecisionDate());
        assertThat(countOf(EmailType.EXPERT_REJECTED)).isEqualTo(1);
    }

    @Test
    void aRealChangeAfterANoOpStillSendsItsMail() throws Exception {
        var e = expert("flip@example.com", ExpertStatus.REJECTED);
        approve(e.getId(), "");
        reject(e.getId(), "", "{}");
        approve(e.getId(), "");
        assertThat(countOf(EmailType.EXPERT_APPROVED)).isEqualTo(2);
        assertThat(countOf(EmailType.EXPERT_REJECTED)).isEqualTo(1);
    }

    @Test
    void aMailPublishedInARolledBackTransactionIsNeverSent() {
        tx.executeWithoutResult(status -> {
            events.publishEvent(new ExpertEmailRequested(EmailType.EXPERT_APPROVED, 1,
                    "ghost@example.com", "Awa", "fr", null));
            status.setRollbackOnly();
        });
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void aSenderThatThrowsNeverBlocksTheRegistrationNorTheDecision() throws Exception {
        mailbox.throwOnSend(new IllegalStateException("smtp exploded"));
        registerAs("ROLE_EXPERT", "boom@example.com", "Awa", "fr");
        assertThat(users.findByEmailIgnoreCase("boom@example.com").orElseThrow().getExpertStatus())
                .isEqualTo(ExpertStatus.PENDING);

        var e = expert("boom2@example.com", ExpertStatus.PENDING);
        approve(e.getId(), "");
        assertThat(users.findById(e.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.APPROVED);
        reject(e.getId(), "", "{\"reason\":\"non\"}");
        assertThat(users.findById(e.getId()).orElseThrow().getExpertStatus()).isEqualTo(ExpertStatus.REJECTED);
    }

    @Test
    void anUnsupportedLangFallsBackToFrench() throws Exception {
        var e = expert("de@example.com", ExpertStatus.PENDING);
        approve(e.getId(), "?lang=de");
        assertThat(mailbox.last().text()).contains("Bonjour Awa");
    }
}
