package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.repositories.ContactMessageRepository;
import com.bowe.meetstudent.services.ContactRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The prod configuration: Spring's ForwardedHeaderFilter ({@code framework} strategy) rewrites
 * {@code getRemoteAddr()} and hides {@code X-Forwarded-For}. The client-controlled RFC 7239 {@code Forwarded}
 * header must never choose the limiter key.
 */
@SpringBootTest(properties = {
        "server.forward-headers-strategy=framework",
        "app.contact.trust-forwarded-for=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactForwardedHeaderIntegrationTests {

    private static final AtomicInteger NEXT = new AtomicInteger(1);
    private static final String BODY = "{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;
    @Autowired private ContactRateLimiter limiter;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private ResultActions send(String remote, String xForwardedFor, String forwarded) throws Exception {
        return mockMvc.perform(post("/api/v1/contact").with(r -> {
            r.setRemoteAddr(remote);
            if (xForwardedFor != null) {
                r.addHeader("X-Forwarded-For", xForwardedFor);
            }
            if (forwarded != null) {
                r.addHeader("Forwarded", forwarded);
            }
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    private static String freshIp() {
        return "203.0.113." + NEXT.getAndIncrement();
    }

    @Test
    void aVaryingForwardedHeaderDoesNotGiveAFreshBudget() throws Exception {
        var proxyHop = freshIp();
        var client = freshIp();
        for (int i = 0; i < 5; i++) {
            send(proxyHop, client, "for=10.9.8." + i).andExpect(status().isAccepted());
        }
        send(proxyHop, client, "for=10.9.8.200").andExpect(status().isTooManyRequests());
        send(proxyHop, client, null).andExpect(status().isTooManyRequests());
    }

    @Test
    void forwardedAloneDoesNotReplaceTheSocketAddress() throws Exception {
        var socket = freshIp();
        for (int i = 0; i < 5; i++) {
            send(socket, null, "for=10.7.7." + i).andExpect(status().isAccepted());
        }
        send(socket, null, "for=10.7.7.250").andExpect(status().isTooManyRequests());
    }

    @Test
    void junkForwardedValuesNeitherBypassNorFillTheLimiter() throws Exception {
        var socket = freshIp();
        var before = limiter.trackedClients();
        for (int i = 0; i < 20; i++) {
            send(socket, null, "for=\"" + "x".repeat(2000) + i + "\"");
        }
        assertThat(limiter.trackedClients()).isLessThanOrEqualTo(before + 1);
    }

    @Test
    void theFirstXForwardedForHopStillIdentifiesTheClient() throws Exception {
        var proxyHop = freshIp();
        var clientA = freshIp();
        var clientB = freshIp();
        for (int i = 0; i < 5; i++) {
            send(proxyHop, clientA, null).andExpect(status().isAccepted());
        }
        send(proxyHop, clientA, null).andExpect(status().isTooManyRequests());
        send(proxyHop, clientB, null).andExpect(status().isAccepted());
    }
}
