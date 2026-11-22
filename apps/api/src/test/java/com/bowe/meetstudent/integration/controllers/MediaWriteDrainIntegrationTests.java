package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.services.MediaStorage;
import com.bowe.meetstudent.services.MediaWriteGate;
import com.bowe.meetstudent.support.ResetStateExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "file.writes-paused=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(ResetStateExtension.class)
class MediaWriteDrainIntegrationTests {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', ' '};

    @TempDir
    static Path mediaRoot;

    @DynamicPropertySource
    static void isolateMediaDirectories(DynamicPropertyRegistry properties) {
        properties.add("file.upload-dir", () -> mediaRoot.resolve("uploads").toString());
        properties.add("file.private-dir", () -> mediaRoot.resolve("private").toString());
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MediaWriteGate mediaWriteGate;
    @MockitoSpyBean
    MediaStorage storageService;

    @AfterEach
    void restoreGateAndStorage() throws Exception {
        doCallRealMethod().when(storageService).delete(anyString());
        mediaWriteGate.pause();
    }

    @Test
    void apiBootsPausedWhenConfiguredAndDrainsInFlightDeferredDeletionsBeforeReportingDrained() throws Exception {
        // 1. Boots directly into paused state when file.writes-paused=true.
        mockMvc.perform(get("/api/v1/admin/media/maintenance")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writesPaused").value(true))
                .andExpect(jsonPath("$.drained").value(true));

        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF))
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

        // 2. Resume and upload a file.
        mockMvc.perform(post("/api/v1/admin/media/maintenance/resume")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writesPaused").value(false));

        String uploadedJson = mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF))
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(uploadedJson).get("id").asInt();

        // 3. Block inside afterCommit() storageService.delete(key) so both in-flight write and
        // pending deferred deletion are active when pause is requested.
        var enteredDelete = new CountDownLatch(1);
        var releaseDelete = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredDelete.countDown();
            assertTrue(releaseDelete.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(storageService).delete(anyString());

        var executor = Executors.newFixedThreadPool(2);
        try {
            var deleteFuture = executor.submit(() -> mockMvc.perform(
                            delete("/api/v1/media/{id}", id).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                    .andReturn().getResponse().getStatus());

            assertTrue(enteredDelete.await(5, TimeUnit.SECONDS));

            // Pause immediately without waiting: maintenance reports paused=true, drained=false,
            // inFlightWrites=1, pendingDeferredDeletions=1.
            mockMvc.perform(post("/api/v1/admin/media/maintenance/pause")
                            .param("waitSeconds", "0")
                            .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.writesPaused").value(true))
                    .andExpect(jsonPath("$.drained").value(false))
                    .andExpect(jsonPath("$.inFlightWrites").value(1))
                    .andExpect(jsonPath("$.pendingDeferredDeletions").value(1));

            // New writes are already rejected while the in-flight deferred deletion drains.
            mockMvc.perform(multipart("/api/v1/media")
                            .file(new MockMultipartFile("file", "other.pdf", "application/pdf", PDF))
                            .param("category", "DIPLOMA")
                            .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                    .andExpect(status().isServiceUnavailable());

            var drainWaitFuture = executor.submit(() -> mockMvc.perform(
                            post("/api/v1/admin/media/maintenance/pause")
                                    .param("waitSeconds", "5")
                                    .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                    .andReturn().getResponse().getContentAsString());

            releaseDelete.countDown();

            assertEquals(204, deleteFuture.get(5, TimeUnit.SECONDS));
            var drainedJson = objectMapper.readTree(drainWaitFuture.get(5, TimeUnit.SECONDS));
            assertTrue(drainedJson.get("drained").asBoolean());
            assertEquals(0, drainedJson.get("inFlightWrites").asInt());
            assertEquals(0, drainedJson.get("pendingDeferredDeletions").asInt());
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }
    }
}
