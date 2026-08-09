package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerIntegrationTests {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', ' '};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0};

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF);
    }

    private int uploadDiplomaAs(int userId) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(userId, "ROLE_STUDENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asInt();
    }

    @Test
    void studentCanUploadDiploma() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void anonymousCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf()).param("category", "DIPLOMA"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotUploadSchoolLogo() throws Exception {
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .param("category", "SCHOOL_LOGO")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanDownloadOwnPrivateDocument() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void otherUserCannotDownloadPrivateDocument() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDownloadPrivateDocument() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsSameMedia() throws Exception {
        String first = mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA").header("Idempotency-Key", "k-1")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA").header("Idempotency-Key", "k-1")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                objectMapper.readTree(first).get("id").asInt(),
                objectMapper.readTree(second).get("id").asInt());
    }

    @Test
    void adminCanVerifyDocumentAndNonAdminCannot() throws Exception {
        int id = uploadDiplomaAs(7);

        mockMvc.perform(patch("/api/v1/media/" + id + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VERIFIED\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        mockMvc.perform(patch("/api/v1/media/" + id + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"reason\":\"x\"}")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mineReturnsOnlyCallersMedia() throws Exception {
        uploadDiplomaAs(7);
        uploadDiplomaAs(8);

        mockMvc.perform(get("/api/v1/media/mine").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
