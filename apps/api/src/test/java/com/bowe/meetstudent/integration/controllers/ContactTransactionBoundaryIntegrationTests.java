package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.repositories.ContactMessageRepository;
import com.bowe.meetstudent.services.ContactRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The limit and the honeypot are decided before any database transaction (and connection) is opened. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactTransactionBoundaryIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;
    @MockitoSpyBean private ContactRateLimiter limiter;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private void send(String ip, String extra) throws Exception {
        mockMvc.perform(post("/api/v1/contact").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\"" + extra + "}"));
    }

    @Test
    void theLimitIsCheckedOutsideAnyTransactionForSuccessHoneypotAndRefusal() throws Exception {
        List<Boolean> activeDuringCheck = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            activeDuringCheck.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(limiter).check(Mockito.anyString());

        send("198.51.100.201", "");
        send("198.51.100.202", ",\"website\":\"spam\"");
        for (int i = 0; i < 6; i++) {
            send("198.51.100.203", "");
        }

        assertThat(activeDuringCheck).hasSize(8).containsOnly(false);
        assertThat(repository.count()).isEqualTo(1 + 5);
    }

    @Test
    void aValidMessageIsStillStoredInsideItsOwnTransaction() throws Exception {
        send("198.51.100.204", "");
        assertThat(repository.count()).isEqualTo(1);
        mockMvc.perform(post("/api/v1/contact").with(r -> {
            r.setRemoteAddr("198.51.100.205");
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\"}")).andExpect(status().isAccepted());
    }
}
