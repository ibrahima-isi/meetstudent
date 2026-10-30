package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.support.ResetStateExtension;
import com.bowe.meetstudent.TestDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ExtendWith(ResetStateExtension.class)
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
    void anonymousCanDownloadPublicMedia() throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .param("category", "SCHOOL_LOGO")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(body).get("id").asInt();

        mockMvc.perform(get("/api/v1/media/" + id)).andExpect(status().isOk());
    }

    @Test
    void anonymousGetOfPrivateMediaIs401() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousGetOfUnknownMediaIs401LikePrivate() throws Exception {
        mockMvc.perform(get("/api/v1/media/987654")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentGetOfUnknownMediaIs403LikeForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/media/987654").with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
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

    private int uploadPublicAs(String category, int userId, String role, String filename) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", filename, "image/png", PNG))
                        .param("category", category)
                        .with(TestDataUtil.mockUser(userId, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asInt();
    }

    @Test
    void studentCannotDeleteAnotherUsersPublicPhoto() throws Exception {
        int id = uploadPublicAs("USER_PHOTO", 7, "ROLE_STUDENT", "me.png");

        mockMvc.perform(delete("/api/v1/media/" + id).with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/media/" + id)).andExpect(status().isOk());
    }

    @Test
    void studentCannotDeleteOwnerlessSchoolLogo() throws Exception {
        int id = uploadPublicAs("SCHOOL_LOGO", 99, "ROLE_ADMIN", "logo.png");

        mockMvc.perform(delete("/api/v1/media/" + id).with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/media/" + id)).andExpect(status().isOk());
    }

    @Test
    void ownerCanDeleteOwnPublicPhoto() throws Exception {
        int id = uploadPublicAs("USER_PHOTO", 7, "ROLE_STUDENT", "me.png");

        mockMvc.perform(delete("/api/v1/media/" + id).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCanDeleteAnyPublicMedia() throws Exception {
        int id = uploadPublicAs("USER_PHOTO", 7, "ROLE_STUDENT", "me.png");

        mockMvc.perform(delete("/api/v1/media/" + id).with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void otherUserCannotDeletePrivateDocumentButOwnerCan() throws Exception {
        int id = uploadDiplomaAs(7);

        mockMvc.perform(delete("/api/v1/media/" + id).with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/media/" + id).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownMediaIs403ForStudent404ForAdminAnd401Anonymous() throws Exception {
        mockMvc.perform(delete("/api/v1/media/987654").with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/media/987654").with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/media/987654")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotDeletePublicMedia() throws Exception {
        int id = uploadPublicAs("USER_PHOTO", 7, "ROLE_STUDENT", "me.png");

        mockMvc.perform(delete("/api/v1/media/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void mineReturnsOnlyCallersMedia() throws Exception {
        uploadDiplomaAs(7);
        uploadDiplomaAs(8);

        mockMvc.perform(get("/api/v1/media/mine").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // --- Upload quotas, per-category types, public headers ---

    private org.springframework.test.web.servlet.ResultActions uploadDiplomaResult(int userId, String role, String key) throws Exception {
        var request = multipart("/api/v1/media").file(pdf())
                .param("category", "DIPLOMA").with(TestDataUtil.mockUser(userId, role));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    @Test
    void twentyPersonalDocumentsAreAcceptedAndTheTwentyFirstIs409() throws Exception {
        for (int i = 0; i < 20; i++) {
            uploadDiplomaAs(7);
        }

        uploadDiplomaResult(7, "ROLE_STUDENT", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // another owner is not affected, and refusing wrote nothing
        uploadDiplomaAs(8);
        mockMvc.perform(get("/api/v1/media/mine").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void deletingADocumentFreesAQuotaSlot() throws Exception {
        int first = uploadDiplomaAs(7);
        for (int i = 1; i < 20; i++) {
            uploadDiplomaAs(7);
        }
        uploadDiplomaResult(7, "ROLE_STUDENT", null).andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/media/" + first).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isNoContent());

        uploadDiplomaResult(7, "ROLE_STUDENT", null).andExpect(status().isCreated());
    }

    @Test
    void adminIsNotLimitedToTwentyDocuments() throws Exception {
        for (int i = 0; i < 21; i++) {
            uploadDiplomaResult(99, "ROLE_ADMIN", null).andExpect(status().isCreated());
        }
    }

    @Test
    void retryWithTheSameKeyAtTheLimitReturnsTheSameMediaInsteadOf409() throws Exception {
        for (int i = 0; i < 19; i++) {
            uploadDiplomaAs(7);
        }
        String first = uploadDiplomaResult(7, "ROLE_STUDENT", "k-last")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String retry = uploadDiplomaResult(7, "ROLE_STUDENT", "k-last")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                objectMapper.readTree(first).get("id").asInt(), objectMapper.readTree(retry).get("id").asInt());
        mockMvc.perform(get("/api/v1/media/mine").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(jsonPath("$.length()").value(20));
    }

    private String publicUrlOf(int userId, String role, String category) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                        .param("category", category)
                        .with(TestDataUtil.mockUser(userId, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("publicUrl").asText();
    }

    @Test
    void secondUserPhotoReplacesTheFirstRowAndFile() throws Exception {
        String firstUrl = publicUrlOf(7, "ROLE_STUDENT", "USER_PHOTO");
        mockMvc.perform(get(firstUrl)).andExpect(status().isOk());

        String secondUrl = publicUrlOf(7, "ROLE_STUDENT", "USER_PHOTO");

        mockMvc.perform(get(firstUrl)).andExpect(status().isNotFound());
        mockMvc.perform(get(secondUrl)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/media/mine").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void userPhotoOfAnotherOwnerIsLeftAlone() throws Exception {
        String otherUrl = publicUrlOf(8, "ROLE_STUDENT", "USER_PHOTO");

        publicUrlOf(7, "ROLE_STUDENT", "USER_PHOTO");

        mockMvc.perform(get(otherUrl)).andExpect(status().isOk());
    }

    @Test
    void pdfAsUserPhotoIs415ButAsDiplomaIs201AndPngAsUserPhotoIs201() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "USER_PHOTO")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isUnsupportedMediaType());
        uploadDiplomaResult(7, "ROLE_STUDENT", null).andExpect(status().isCreated());
        publicUrlOf(7, "ROLE_STUDENT", "USER_PHOTO");
    }

    @Test
    void pdfAsSchoolLogoIs415EvenForAdmin() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "SCHOOL_LOGO")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void staticPublicFilesCarrySandboxCspAndNosniff() throws Exception {
        String url = publicUrlOf(99, "ROLE_ADMIN", "SCHOOL_LOGO");

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
