package com.bowe.meetstudent.integration.security;

import com.bowe.meetstudent.security.JwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a real servlet container: MockMvc never performs the container error
 * dispatch to /error, which is where access denials used to turn into 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorDispatchIntegrationTests {

    @LocalServerPort private int port;
    @Autowired private JwtIssuer jwtIssuer;

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString("{}"));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void studentOnAdminUrlRuleGets403() throws Exception {
        String token = jwtIssuer.issueToken(1, "s@example.com", List.of("ROLE_STUDENT"));
        var response = send("POST", "/api/v1/schools", token);
        assertEquals(403, response.statusCode(), response.body());
        assertTrue(response.body().contains("Forbidden"), response.body());
    }

    @Test
    void missingTokenOnAdminUrlRuleGets401() throws Exception {
        assertEquals(401, send("POST", "/api/v1/schools", null).statusCode());
    }

    @Test
    void invalidTokenOnAdminUrlRuleGets401() throws Exception {
        assertEquals(401, send("POST", "/api/v1/schools", "garbage").statusCode());
    }
}
