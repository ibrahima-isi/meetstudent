package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Use standard 'test' profile
@Transactional // Rollback DB after each test
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Ensure roles exist (Flyway might have run, but H2 can be tricky with profiles)
        // We check and insert if needed just to be safe for the test context
        if (roleRepository.findByName("ROLE_STUDENT").isEmpty()) {
            roleRepository.save(Role.builder().name("ROLE_STUDENT").description("User").build());
        }
    }

    @Test
    void passwordChangeRequiresCurrentPasswordWithoutChangingProfileOrSession() throws Exception {
        var user = userService.saveUser(com.bowe.meetstudent.entities.UserEntity.builder()
                .email("change@test.com").password("password123").firstname("Original")
                .role(roleRepository.findByName("ROLE_STUDENT").orElseThrow()).build(), passwordEncoder);
        var login = mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"change@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var access = objectMapper.readTree(login).get("accessToken").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstname\":\"Changed\",\"password\":\"newPassword123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.currentPassword").exists());
        mockMvc.perform(get("/api/v1/users/id/" + user.getId()).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstname").value("Original"));
    }

    @Test
    void passwordChangeRevokesEveryAccessAndRefreshCredentialAndAllowsNewLogin() throws Exception {
        var user = userService.saveUser(com.bowe.meetstudent.entities.UserEntity.builder()
                .email("revoke@test.com").password("password123").firstname("Original")
                .role(roleRepository.findByName("ROLE_STUDENT").orElseThrow()).build(), passwordEncoder);
        var login = mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"revoke@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tokens = objectMapper.readTree(login);
        var access = tokens.get("accessToken").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newPassword123\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/id/" + user.getId()).header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", tokens.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        var fresh = mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"revoke@test.com\",\"password\":\"newPassword123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/v1/users/id/" + user.getId())
                .header("Authorization", "Bearer " + objectMapper.readTree(fresh).get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeCredentials() throws Exception {
        var user = account("wrong-current@test.com", "ROLE_STUDENT");
        var tokens = credentials(user.getEmail(), "password123");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newPassword123\",\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.currentPassword").exists());
        credentials(user.getEmail(), "password123");
    }

    @Test
    void adminExemptionAppliesOnlyWhenEditingAnotherAccount() throws Exception {
        var admin = account("admin-change@test.com", "ROLE_ADMIN");
        var student = account("student-change@test.com", "ROLE_STUDENT");
        var studentTokens = credentials(student.getEmail(), "password123");
        var adminTokens = credentials(admin.getEmail(), "password123");
        var auth = "Bearer " + adminTokens.get("accessToken").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/" + admin.getId())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newPassword123\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/" + student.getId())
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newPassword123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/id/" + student.getId())
                .header("Authorization", "Bearer " + studentTokens.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/id/" + admin.getId()).header("Authorization", auth))
                .andExpect(status().isOk());
        credentials(student.getEmail(), "newPassword123");
    }

    @Autowired private com.bowe.meetstudent.security.JwtProperties jwtProperties;

    @Test
    void legacyCredentialsWorkUntilFirstChangeAndNeverBecomeValidAgain() throws Exception {
        var user = account("legacy@test.com", "ROLE_STUDENT");
        // Old deployment format: no pv claim and a refresh row using the migration's zero default.
        var legacy = com.auth0.jwt.JWT.create().withSubject(user.getId().toString())
                .withClaim("email", user.getEmail()).withClaim("roles", java.util.List.of("ROLE_STUDENT"))
                .withExpiresAt(java.time.Instant.now().plusSeconds(300))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(jwtProperties.getSecretKey()));
        refreshTokenRepository.save(com.bowe.meetstudent.entities.RefreshToken.builder().user(user)
                .sessionId(java.util.UUID.randomUUID()).lastActivity(java.time.Instant.now()).legacy(true)
                .token(sha256Hex("legacy-refresh")).expiryDate(java.time.Instant.now().plusSeconds(300)).build());
        mockMvc.perform(get("/api/v1/users/id/" + user.getId()).header("Authorization", "Bearer " + legacy))
                .andExpect(status().isOk());
        var rotated = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"legacy-refresh\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var first = objectMapper.readTree(rotated);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + legacy).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"secondPassword\",\"currentPassword\":\"password123\"}"))
                .andExpect(status().isOk());
        var second = credentials(user.getEmail(), "secondPassword");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                .header("Authorization", "Bearer " + second.get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password123\",\"currentPassword\":\"secondPassword\"}"))
                .andExpect(status().isOk());
        for (var access : java.util.List.of(legacy, first.get("accessToken").asText(), second.get("accessToken").asText())) {
            mockMvc.perform(get("/api/v1/users/id/" + user.getId()).header("Authorization", "Bearer " + access))
                    .andExpect(status().isUnauthorized());
        }
        for (var refresh : java.util.List.of("legacy-refresh", first.get("refreshToken").asText(), second.get("refreshToken").asText())) {
            mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refresh))))
                    .andExpect(status().isUnauthorized());
        }
        credentials(user.getEmail(), "password123");
    }

    @Test
    void refreshTokensAreStoredOnlyAsTheirSha256() throws Exception {
        var user = account("hashed@test.com", "ROLE_STUDENT");
        var issued = credentials(user.getEmail(), "password123").get("refreshToken").asText();
        assertThat(refreshTokenRepository.findByToken(issued)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(sha256Hex(issued))).isPresent();

        var rotated = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", issued))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        assertThat(rotated).isNotEqualTo(issued);
        assertThat(refreshTokenRepository.findByToken(rotated)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(sha256Hex(rotated))).isPresent();
        assertThat(refreshTokenRepository.findByToken(sha256Hex(issued))).isEmpty();
    }

    @Test
    void storedHashIsNotAcceptedAsARefreshToken() throws Exception {
        var user = account("hash-replay@test.com", "ROLE_STUDENT");
        var issued = credentials(user.getEmail(), "password123").get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", sha256Hex(issued)))))
                .andExpect(status().isUnauthorized());
    }

    /** What the database holds for a refresh token: its SHA-256 in lowercase hex, as V35 computes it. */
    static String sha256Hex(String raw) throws java.security.NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private com.bowe.meetstudent.entities.UserEntity account(String email, String role) {
        var assigned = roleRepository.findByName(role).orElseGet(() -> roleRepository.save(Role.builder().name(role).build()));
        return userService.saveUser(com.bowe.meetstudent.entities.UserEntity.builder().email(email)
                .password("password123").firstname("Original").role(assigned).build(), passwordEncoder);
    }

    private com.fasterxml.jackson.databind.JsonNode credentials(String email, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("username", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        // 1. Create a User in DB
        String email = "integration@test.com";
        String rawPassword = "password123";
        
        // We create entity directly or via service
        // Creating role first
        Role userRole = roleRepository.findByName("ROLE_STUDENT").orElseThrow();

        // Note: Using Service is better to ensure encoding, but we can do it manually for speed/control
        // However, userService.saveUser encodes password.
        
        com.bowe.meetstudent.entities.UserEntity user = com.bowe.meetstudent.entities.UserEntity.builder()
                .email(email)
                .password(rawPassword) // Service will encode this
                .firstname("Test")
                .lastname("User")
                .role(userRole)
                .build();
        
        userService.saveUser(user, passwordEncoder);

        // 2. Perform Login Request
        LoginRequest loginRequest = LoginRequest.builder()
                .username(email)
                .password(rawPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void shouldRefreshAccessTokenSuccessfully() throws Exception {
        // 1. Create User
        String email = "refresh@test.com";
        String rawPassword = "password123";
        Role userRole = roleRepository.findByName("ROLE_STUDENT").orElseThrow();

        com.bowe.meetstudent.entities.UserEntity user = com.bowe.meetstudent.entities.UserEntity.builder()
                .email(email)
                .password(rawPassword)
                .firstname("Test")
                .lastname("User")
                .role(userRole)
                .build();
        userService.saveUser(user, passwordEncoder);


        // 2. Login to get initial tokens
        LoginRequest loginRequest = LoginRequest.builder().username(email).password(rawPassword).build();
        String loginResponse = mockMvc.perform(post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();
        
        com.bowe.meetstudent.models.LoginResponse response = objectMapper.readValue(loginResponse, com.bowe.meetstudent.models.LoginResponse.class);
        String refreshToken = response.getRefreshToken();

        // 3. Use Refresh Token
        com.bowe.meetstudent.models.TokenRefreshRequest refreshRequest = new com.bowe.meetstudent.models.TokenRefreshRequest();
        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(refreshToken))); // Rotation check
    }

    @Test
    void shouldFailLogin_whenPasswordIsWrong() throws Exception {
        // 1. Create a User
        String email = "wrongpass@test.com";
        Role userRole = roleRepository.findByName("ROLE_STUDENT").orElseThrow();
        
        com.bowe.meetstudent.entities.UserEntity user = com.bowe.meetstudent.entities.UserEntity.builder()
                .email(email)
                .password("correctPassword")
                .firstname("Test")
                .lastname("User")
                .role(userRole)
                .build();
        
        userService.saveUser(user, passwordEncoder);

        // 2. Try Login with WRONG password
        LoginRequest loginRequest = LoginRequest.builder()
                .username(email)
                .password("wrongPassword")
                .build();

        mockMvc.perform(post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized()); // Expect 401
    }

    @Autowired
    private com.bowe.meetstudent.repositories.RefreshTokenRepository refreshTokenRepository;

    @Test
    void shouldReturn401_whenRefreshTokenIsUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"does-not-exist\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Refresh token is not in database!"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/refresh"));
    }

    @Test
    void shouldReturn401_whenRefreshTokenIsExpired() throws Exception {
        Role role = roleRepository.findByName("ROLE_STUDENT").orElseThrow();
        var user = userService.saveUser(com.bowe.meetstudent.entities.UserEntity.builder()
                .email("expired@test.com").password("password123").firstname("E").lastname("X")
                .role(role).build(), passwordEncoder);
        refreshTokenRepository.save(com.bowe.meetstudent.entities.RefreshToken.builder()
                .user(user).token(sha256Hex("expired-token")).sessionId(java.util.UUID.randomUUID())
                .lastActivity(java.time.Instant.now().minus(java.time.Duration.ofDays(8)))
                .expiryDate(java.time.Instant.now().minusSeconds(60)).build());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void shouldFailAccessSecuredEndpoint_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users")) // Assuming this is secured now
                .andExpect(status().isUnauthorized()); // Expect 401
    }
}
