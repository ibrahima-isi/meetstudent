package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import com.bowe.meetstudent.mail.SendOutcome;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import com.bowe.meetstudent.services.ContactMailStatusService;
import com.bowe.meetstudent.support.CapturingEmailSender;
import com.bowe.meetstudent.support.CapturingMailConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The delivery result is saved on the message row. Runs with the synchronous executors of the test profile and a
 * zero retry delay, so the retries are real but nothing sleeps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapturingMailConfig.class)
@TestPropertySource(properties = "app.contact.mail.retry.initial-delay=0s")
class ContactMailDeliveryIntegrationTests {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;
    @Autowired private CapturingEmailSender mailbox;
    @Autowired private ContactMailStatusService recorder;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        mailbox.clear();
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
        mailbox.clear();
    }

    private void submit(String extra) throws Exception {
        mockMvc.perform(post("/api/v1/contact")
                        .with(r -> {
                            r.setRemoteAddr("203.0.113." + NEXT_IP.getAndIncrement());
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\"" + extra + "}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void aDeliveredMessageIsSentWithOneAttemptAndATimestamp() throws Exception {
        submit("");

        var row = repository.findAll().get(0);
        assertThat(row.getMailStatus()).isEqualTo(ContactMailStatus.SENT);
        assertThat(row.getMailAttempts()).isEqualTo(1);
        assertThat(row.getMailSentAt()).isNotNull();
    }

    @Test
    void aTransientFailureIsRetriedThenRecordedAsFailedAndTheAnswerIsStill202() throws Exception {
        mailbox.respondWith(SendOutcome.CONNECTION_FAILED);
        submit("");

        var row = repository.findAll().get(0);
        assertThat(row.getMailStatus()).isEqualTo(ContactMailStatus.FAILED);
        assertThat(row.getMailAttempts()).isEqualTo(3);
        assertThat(row.getMailSentAt()).isNull();
        assertThat(mailbox.all()).hasSize(3);
    }

    @Test
    void aPermanentFailureIsNotRetried() throws Exception {
        mailbox.respondWith(SendOutcome.AUTH_FAILED);
        submit("");

        var row = repository.findAll().get(0);
        assertThat(row.getMailStatus()).isEqualTo(ContactMailStatus.FAILED);
        assertThat(row.getMailAttempts()).isEqualTo(1);
        assertThat(mailbox.all()).hasSize(1);
    }

    @Test
    void aDisabledMailerSkipsTheMessage() throws Exception {
        mailbox.respondWith(SendOutcome.DISABLED);
        submit("");

        var row = repository.findAll().get(0);
        assertThat(row.getMailStatus()).isEqualTo(ContactMailStatus.SKIPPED);
        assertThat(row.getMailAttempts()).isZero();
    }

    @Test
    void theHoneypotStoresAndSendsNothing() throws Exception {
        submit(",\"website\":\"http://spam.example\"");

        assertThat(repository.count()).isZero();
        assertThat(mailbox.all()).isEmpty();
    }

    @Test
    void theResultIsSavedInItsOwnTransactionEvenWhenTheCallerRollsBack() throws Exception {
        submit("");
        mailbox.clear();
        var id = repository.findAll().get(0).getId();

        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            recorder.record(id, ContactMailStatus.FAILED, 2, null);
            tx.setRollbackOnly();
        });

        var row = repository.findById(id).orElseThrow();
        assertThat(row.getMailStatus()).isEqualTo(ContactMailStatus.FAILED);
        assertThat(row.getMailAttempts()).isEqualTo(2);
    }

    @Test
    void recordingForAnUnknownMessageIsANoOp() {
        assertThatCode(() -> recorder.record(987654, ContactMailStatus.SENT, 1, Instant.now())).doesNotThrowAnyException();
    }
}
