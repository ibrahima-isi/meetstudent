package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.repositories.ContactMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real servlet container (MockMvc has no Tomcat valve): the proxy headers count only when the socket peer is in
 * {@code server.tomcat.remoteip.internal-proxies}. In prod that is the dedicated network shared with Traefik
 * (compose.coolify.yml), so a neighbour container calling the API directly cannot choose its rate-limit key.
 * The test client connects from 127.0.0.1, so the property decides whether it is "the proxy" or "a neighbour".
 */
class ContactTrustedProxyIntegrationTests {

    private static final AtomicInteger NEXT = new AtomicInteger(1);
    private static final String BODY = "{\"email\":\"awa@example.com\",\"subject\":\"s\",\"message\":\"m\"}";

    private static String freshIp() {
        return "198.51.100." + NEXT.getAndIncrement();
    }

    private static int post(int port, String xForwardedFor, String forwarded) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/contact"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(BODY));
        if (xForwardedFor != null) {
            request.header("X-Forwarded-For", xForwardedFor);
        }
        if (forwarded != null) {
            request.header("Forwarded", forwarded);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    /** The peer (127.0.0.1) is not a trusted proxy: what it claims in the headers is ignored. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "server.forward-headers-strategy=native",
            "server.tomcat.remoteip.internal-proxies=10\\.250\\.77\\.\\d{1,3}",
            "app.contact.trust-forwarded-for=false"
    })
    @ActiveProfiles("test")
    class FromAnUntrustedPeer {

        @LocalServerPort private int port;
        @Autowired private ContactMessageRepository repository;

        @AfterEach
        void cleanUp() {
            repository.deleteAll();
        }

        @Test
        void forgedForwardingHeadersNeverGiveAFreshBudget() throws Exception {
            for (int i = 0; i < 5; i++) {
                assertEquals(202, post(port, freshIp(), "for=" + freshIp()));
            }
            assertEquals(429, post(port, freshIp(), "for=" + freshIp()));
        }
    }

    /** The peer is listed as the proxy: the client address in X-Forwarded-For identifies the visitor. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "server.forward-headers-strategy=native",
            "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
            "app.contact.trust-forwarded-for=false"
    })
    @ActiveProfiles("test")
    class FromTheTrustedProxy {

        @LocalServerPort private int port;
        @Autowired private ContactMessageRepository repository;

        @AfterEach
        void cleanUp() {
            repository.deleteAll();
        }

        @Test
        void eachClientBehindTheProxyHasItsOwnBudget() throws Exception {
            var visitorA = freshIp();
            for (int i = 0; i < 5; i++) {
                assertEquals(202, post(port, visitorA, null));
            }
            assertEquals(429, post(port, visitorA, null));
            assertEquals(202, post(port, freshIp(), null));
        }

        @Test
        void theRfc7239ForwardedHeaderStillChoosesNothing() throws Exception {
            var visitor = freshIp();
            for (int i = 0; i < 5; i++) {
                assertEquals(202, post(port, visitor, "for=" + freshIp()));
            }
            assertEquals(429, post(port, visitor, "for=" + freshIp()));
        }
    }
}
