package com.bowe.meetstudent.integration.controllers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.SendOutcome;
import com.bowe.meetstudent.repositories.RefreshTokenRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.UserTokenRepository;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The SMTP self-test through the HTTP layer, with a stub sender that answers each outcome. Cooldown off here. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
@TestPropertySource(properties = "app.mail.test-cooldown-seconds=0")
class AdminMailTestIntegrationTests {

    private static final String URL = "/api/v1/admin/mail/test";
    private static final String ADMIN_EMAIL = "boss.secret@admin-example.org";

    @Autowired private MockMvc mockMvc;
    @Autowired private CapturingEmailSender mailbox;
    @Autowired private UserRepository userRepository;
    @Autowired private UserTokenRepository userTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Integer adminId;
    private ListAppender<ILoggingEvent> logs;
    private Logger rootLogger;

    @BeforeEach
    void setUp() {
        mailbox.clear();
        var admin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Admin").build()));
        adminId = userService.saveUser(UserEntity.builder().firstname("Boss").lastname("Admin").email(ADMIN_EMAIL)
                .password("S3cure-test-pass").role(admin).build(), passwordEncoder).getId();
        rootLogger = (Logger) LoggerFactory.getLogger("com.bowe.meetstudent");
        rootLogger.setLevel(Level.ALL);
        logs = new ListAppender<>();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void cleanUp() {
        rootLogger.detachAppender(logs);
        userTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        mailbox.clear();
    }

    @Test
    void anonymousIs401AndNonAdminsAre403AndNothingIsSent() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
        for (var role : new String[]{"ROLE_STUDENT", "ROLE_EXPERT"}) {
            mockMvc.perform(post(URL).with(TestDataUtil.mockUser(role))).andExpect(status().isForbidden());
        }
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void onlyPostIsAllowed() throws Exception {
        mockMvc.perform(get(URL).with(TestDataUtil.mockUser(adminId, "ROLE_ADMIN"))).andExpect(status().is4xxClientError());
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void aDeliveredTestAnswers200SentAndGoesOnlyToTheAdminsOwnAddressInBothLanguages() throws Exception {
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(adminId, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"SENT\"}", true));

        assertThat(mailbox.all()).hasSize(1);
        var mail = mailbox.last();
        assertThat(mail.type()).isEqualTo(EmailType.MAIL_TEST);
        assertThat(mail.to()).isEqualTo(ADMIN_EMAIL);
        assertThat(mail.replyTo()).isNull();
        assertThat(mail.subject()).contains("Test").contains("SMTP");
        assertThat(mail.text()).contains("Ce message").contains("This message");
        assertThat(mail.html()).contains("Ce message").contains("This message");
        assertThat(mail.subject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void aRequestBodyCannotChangeTheRecipient() throws Exception {
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(adminId, "ROLE_ADMIN"))
                        .contentType("application/json").content("{\"to\":\"victim@example.com\",\"email\":\"victim@example.com\"}"))
                .andExpect(status().isOk());
        assertThat(mailbox.all()).hasSize(1);
        assertThat(mailbox.last().to()).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void aMailOnlyWrittenToTheLogAnswers200LoggedNotSent() throws Exception {
        mailbox.respondWith(SendOutcome.LOGGED);
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(adminId, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"LOGGED\"}", true));
    }

    @Test
    void everyFailureOutcomeAnswers502WithItsName() throws Exception {
        for (var outcome : new SendOutcome[]{SendOutcome.AUTH_FAILED, SendOutcome.CONNECTION_FAILED, SendOutcome.TLS_FAILED,
                SendOutcome.REJECTED, SendOutcome.FAILED}) {
            mailbox.clear();
            mailbox.respondWith(outcome);
            mockMvc.perform(post(URL).with(TestDataUtil.mockUser(adminId, "ROLE_ADMIN")))
                    .andExpect(status().isBadGateway())
                    .andExpect(content().json("{\"status\":\"" + outcome.name() + "\"}", true));
        }
    }

    @Test
    void aDisabledSenderAnswers503Disabled() throws Exception {
        mailbox.respondWith(SendOutcome.DISABLED);
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(adminId, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DISABLED\"}", true));
    }

    @Test
    void anAdminWhoNoLongerExistsIs404AndNothingIsSent() throws Exception {
        mockMvc.perform(post(URL).with(TestDataUtil.mockUser(987654, "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void neitherTheResponsesNorTheLogsEverContainTheAddress() throws Exception {
        var bodies = new StringBuilder();
        for (var outcome : SendOutcome.values()) {
            mailbox.respondWith(outcome);
            var response = mockMvc.perform(post(URL).with(TestDataUtil.mockUser(adminId, "ROLE_ADMIN"))).andReturn().getResponse();
            bodies.append(response.getContentAsString()).append(response.getHeaderNames());
        }
        assertThat(bodies.toString()).doesNotContain(ADMIN_EMAIL).doesNotContain("admin-example");
        var all = new StringBuilder();
        logs.list.forEach(e -> all.append(e.getFormattedMessage()).append('\n'));
        assertThat(all.toString()).contains("Mail test").doesNotContain(ADMIN_EMAIL).doesNotContain("admin-example");
    }
}
