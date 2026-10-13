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
    @Autowired private com.bowe.meetstudent.repositories.UserRepository users;

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
        var user = users.save(com.bowe.meetstudent.entities.UserEntity.builder().email("s@example.com").build());
        String token = jwtIssuer.issueToken(user.getId(), user.getEmail(), List.of("ROLE_STUDENT"), 0);
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
