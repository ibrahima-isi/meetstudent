package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.SendOutcome;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Non-transactional: the mail goes out from an AFTER_COMMIT listener. Synchronous executors, zero retry delay. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
@TestPropertySource(properties = "app.contact.mail.retry.initial-delay=0s")
class AdminMessageResendIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;
    @Autowired private CapturingEmailSender mailbox;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
        mailbox.clear();
    }

    private ContactMessage failedMessage() {
        return repository.save(ContactMessage.builder()
                .senderEmail("awa@example.com").subject("Question").body("Bonjour <b>team</b>")
                .language("en").createdAt(Instant.parse("2026-03-01T10:00:00Z"))
                .mailStatus(ContactMailStatus.FAILED).mailAttempts(3).build());
    }

    private static String url(Integer id) {
        return "/api/v1/admin/messages/" + id + "/resend";
    }

    @Test
    void anonymousIs401AndNonAdminsAre403() throws Exception {
        var saved = failedMessage();
        mockMvc.perform(post(url(saved.getId()))).andExpect(status().isUnauthorized());
        for (var role : new String[]{"ROLE_STUDENT", "ROLE_EXPERT"}) {
            mockMvc.perform(post(url(saved.getId())).with(TestDataUtil.mockUser(role))).andExpect(status().isForbidden());
        }
        assertThat(mailbox.all()).isEmpty();
        assertThat(repository.findById(saved.getId()).orElseThrow().getMailStatus()).isEqualTo(ContactMailStatus.FAILED);
    }

    @Test
    void resendQueuesTheMailAgainAndTheResultIsSaved() throws Exception {
        var saved = failedMessage();

        mockMvc.perform(post(url(saved.getId())).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        assertThat(mailbox.all()).hasSize(1);
        var mail = mailbox.last();
        assertThat(mail.type()).isEqualTo(EmailType.CONTACT_MESSAGE);
        assertThat(mail.to()).isEqualTo("contact@meetstudent.com");
        assertThat(mail.replyTo()).isEqualTo("awa@example.com");
        assertThat(mail.subject()).isEqualTo("[Contact] Question");
        assertThat(mail.text()).contains("Bonjour <b>team</b>");

        var row = repository.findById(saved.getId()).orElseThrow();
        assertThat(row.getMailStatus()).isEqualTo(ContactMailStatus.SENT);
        assertThat(row.getMailAttempts()).isEqualTo(1);
        assertThat(row.getMailSentAt()).isNotNull();
    }

    @Test
    void aResendThatFailsAgainEndsFailedWithItsOwnAttemptCount() throws Exception {
        var saved = failedMessage();
        mailbox.respondWith(SendOutcome.AUTH_FAILED);

        mockMvc.perform(post(url(saved.getId())).with(TestDataUtil.mockUser("ROLE_ADMIN"))).andExpect(status().isAccepted());

        var row = repository.findById(saved.getId()).orElseThrow();
        assertThat(row.getMailStatus()).isEqualTo(ContactMailStatus.FAILED);
        assertThat(row.getMailAttempts()).isEqualTo(1);
        assertThat(row.getMailSentAt()).isNull();
    }

    @Test
    void anUnknownMessageIs404WithTheStandardBody() throws Exception {
        mockMvc.perform(post(url(987654)).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
        assertThat(mailbox.all()).isEmpty();
    }
}
