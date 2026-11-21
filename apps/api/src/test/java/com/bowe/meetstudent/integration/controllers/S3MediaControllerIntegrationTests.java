package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.support.ResetStateExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(ResetStateExtension.class)
@EnabledIfEnvironmentVariable(named = "MEDIA_S3_TEST_ENDPOINT", matches = ".+")
class S3MediaControllerIntegrationTests {

    private static final String BUCKET = "meetstudent-media-" + UUID.randomUUID();
    private static final String ACCESS_KEY = "meetstudent-test";
    private static final String SECRET_KEY = "meetstudent-test-secret";
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', ' '};

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0};

    private static S3Client fixture;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry properties) {
        var endpoint = URI.create(System.getenv("MEDIA_S3_TEST_ENDPOINT"));
        fixture = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .build();
        fixture.createBucket(request -> request.bucket(BUCKET));
        properties.add("file.storage-backend", () -> "s3");
        properties.add("file.s3.endpoint", endpoint::toString);
        properties.add("file.s3.bucket", () -> BUCKET);
        properties.add("file.s3.region", () -> "us-east-1");
        properties.add("file.s3.path-style", () -> true);
        properties.add("file.s3.access-key-id", () -> ACCESS_KEY);
        properties.add("file.s3.secret-access-key", () -> SECRET_KEY);
    }

    @AfterEach
    void deleteFixtureObjects() {
        clearFixtureBucket();
    }

    private static void clearFixtureBucket() {
        fixture.listObjectsV2Paginator(request -> request.bucket(BUCKET)).contents().forEach(object ->
                fixture.deleteObject(request -> request.bucket(BUCKET).key(object.key())));
    }

    @AfterAll
    static void deleteFixtureBucket() {
        if (fixture != null) {
            try (var client = fixture) {
                clearFixtureBucket();
                client.deleteBucket(request -> request.bucket(BUCKET));
            }
        }
    }

    @Test
    void ownerCanUploadAndReadPrivateMediaThroughMeetStudent() throws Exception {
        var pdf = new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF);
        var uploaded = upload(pdf, "DIPLOMA");
        int id = uploaded.get("id").asInt();
        assertThat(uploaded.has("storageKey")).isFalse();

        mockMvc.perform(get("/api/v1/media/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/media/{id}", id).with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/media/{id}", id).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/api/v1/media/{id}", id).with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));

        var key = fixture.listObjectsV2(request -> request.bucket(BUCKET).prefix("private/"))
                .contents().getFirst().key();
        mockMvc.perform(get("/uploads/" + key)).andExpect(status().isUnauthorized());
        assertProviderDeniesAnonymousAccess(key);
    }

    @Test
    void legacyPublicUrlStreamsOnlyTheRequestedRange() throws Exception {
        var key = "public/legacy/school-logo.png";
        fixture.putObject(request -> request.bucket(BUCKET).key(key), RequestBody.fromBytes(PNG));

        var response = mockMvc.perform(get("/uploads/" + key))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andReturn().getResponse();
        mockMvc.perform(get("/uploads/" + key).header(HttpHeaders.IF_MODIFIED_SINCE,
                        response.getHeader(HttpHeaders.LAST_MODIFIED)))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/uploads/" + key).header(HttpHeaders.RANGE, "bytes=2-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-4/10"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "3"))
                .andExpect(content().bytes(new byte[]{0x4e, 0x47, 0}));
        assertProviderDeniesAnonymousAccess(key);
    }

    @Test
    void replacingAndDeletingPhotosRemovesTheOldObjects() throws Exception {
        var first = upload(new MockMultipartFile("file", "me.png", "image/png", PNG), "USER_PHOTO");
        var firstUrl = first.get("publicUrl").asText();
        mockMvc.perform(get(firstUrl)).andExpect(status().isOk()).andExpect(content().bytes(PNG));

        var second = upload(new MockMultipartFile("file", "other.png", "image/png", PNG), "USER_PHOTO");
        var secondUrl = second.get("publicUrl").asText();
        assertThat(secondUrl).isNotEqualTo(firstUrl);
        mockMvc.perform(get(firstUrl)).andExpect(status().isNotFound());
        mockMvc.perform(get(secondUrl)).andExpect(status().isOk()).andExpect(content().bytes(PNG));

        mockMvc.perform(delete("/api/v1/media/{id}", second.get("id").asInt())
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(secondUrl)).andExpect(status().isNotFound());
        assertThat(fixture.listObjectsV2(request -> request.bucket(BUCKET)).contents()).isEmpty();
    }

    @Test
    void distinguishesMissingObjectsFromAnUnavailableBucket() throws Exception {
        String url = "/uploads/public/missing.png";
        mockMvc.perform(get(url)).andExpect(status().isNotFound());

        fixture.deleteBucket(request -> request.bucket(BUCKET));
        try {
            mockMvc.perform(get(url))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("Media storage is temporarily unavailable"));
        } finally {
            fixture.createBucket(request -> request.bucket(BUCKET));
        }
    }

    private static void assertProviderDeniesAnonymousAccess(String key) throws Exception {
        var url = URI.create(System.getenv("MEDIA_S3_TEST_ENDPOINT") + "/" + BUCKET + "/" + key);
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(url).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(403);
        }
    }

    private JsonNode upload(MockMultipartFile file, String category) throws Exception {
        var result = mockMvc.perform(multipart("/api/v1/media").file(file)
                        .param("category", category).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
