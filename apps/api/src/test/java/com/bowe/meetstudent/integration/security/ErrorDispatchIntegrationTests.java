package com.bowe.meetstudent.integration.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
    @Autowired private com.bowe.meetstudent.repositories.RoleRepository roles;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
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
        var role = roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(
                com.bowe.meetstudent.entities.Role.builder().name("ROLE_STUDENT").build()));
        var user = users.save(com.bowe.meetstudent.entities.UserEntity.builder().email("s@example.com")
                .role(role).password(passwords.encode("disposable-password")).build());
        var login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of(
                        "username", user.getEmail(), "password", "disposable-password")))).build();
        var credentials = HttpClient.newHttpClient().send(login, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, credentials.statusCode(), credentials.body());
        String token = json.readTree(credentials.body()).get("accessToken").asText();
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
