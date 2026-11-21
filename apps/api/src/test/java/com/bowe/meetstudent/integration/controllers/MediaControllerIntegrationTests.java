package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.support.ResetStateExtension;
import com.bowe.meetstudent.TestDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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
    MediaRepository mediaRepository;

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
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "10"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void privateDownloadsSupportClosedSuffixAndOpenEndedRanges() throws Exception {
        int id = uploadDiplomaAs(7);

        downloadPrivateRange(id, "bytes=2-4")
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-4/10"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "3"))
                .andExpect(content().bytes(new byte[]{'D', 'F', '-'}));
        downloadPrivateRange(id, "bytes=-3")
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 7-9/10"))
                .andExpect(content().bytes(new byte[]{'4', '\n', ' '}));
        downloadPrivateRange(id, "bytes=5-")
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 5-9/10"))
                .andExpect(content().bytes(new byte[]{'1', '.', '4', '\n', ' '}));
    }

    @Test
    void privateDownloadsReturnMultipartRanges() throws Exception {
        int id = uploadDiplomaAs(7);

        downloadPrivateRange(id, "bytes=0-1,4-5")
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("multipart/byteranges; boundary=")))
                .andExpect(content().string(containsString("Content-Range: bytes 0-1/10\r\n\r\n%P")))
                .andExpect(content().string(containsString("Content-Range: bytes 4-5/10\r\n\r\n-1")));
    }

    @Test
    void privateDownloadsReportUnsatisfiableRanges() throws Exception {
        int id = uploadDiplomaAs(7);

        downloadPrivateRange(id, "bytes=10-")
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"));
    }

    private ResultActions downloadPrivateRange(int id, String range) throws Exception {
        return mockMvc.perform(get("/api/v1/media/" + id).header(HttpHeaders.RANGE, range)
                .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")));
    }

    @Test
    void otherUserCannotDownloadPrivateDocument() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id).header(HttpHeaders.RANGE, "bytes=0-1")
                        .with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE));
    }

    @Test
    void aMissingPrivateFileIsReportedOnlyAfterCheckingAccess() throws Exception {
        int id = mediaRepository.save(Media.builder()
                .category(MediaCategory.DIPLOMA)
                .visibility(MediaVisibility.PRIVATE)
                .ownerId(7)
                .storageKey("private/missing-diploma.pdf")
                .build()).getId();

        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isNotFound());
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

    private ResultActions uploadDiplomaResult(int userId, String role, String key) throws Exception {
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
    void aFailedPhotoReplacementKeepsThePreviousRowAndFile() throws Exception {
        String previousUrl = publicUrlOf(7, "ROLE_STUDENT", "USER_PHOTO");
        Path publicRoot = mediaRoot.resolve("uploads/public");
        Path parked = Files.move(publicRoot, publicRoot.resolveSibling("parked-public"));
        try {
            Files.write(publicRoot, PNG);
            mockMvc.perform(multipart("/api/v1/media")
                            .file(new MockMultipartFile("file", "replacement.png", "image/png", PNG))
                            .param("category", "USER_PHOTO")
                            .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                    .andExpect(status().isInternalServerError());
        } finally {
            Files.deleteIfExists(publicRoot);
            Files.move(parked, publicRoot);
        }

        mockMvc.perform(get("/api/v1/media/mine").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicUrl").value(previousUrl));
        mockMvc.perform(get(previousUrl)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
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
    void publicFilesCarrySandboxCspAndNosniff() throws Exception {
        String url = publicUrlOf(99, "ROLE_ADMIN", "SCHOOL_LOGO");

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void aLegacyPublicUrlSupportsFullHeadConditionalAndRangeReads() throws Exception {
        Path legacyFolder = Files.createDirectories(mediaRoot.resolve("uploads/public/legacy"));
        Files.write(legacyFolder.resolve("old logo.png"), PNG);
        String url = "/uploads/public/legacy/old logo.png";

        String lastModified = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"))
                .andReturn().getResponse().getHeader(HttpHeaders.LAST_MODIFIED);
        mockMvc.perform(head(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "10"))
                .andExpect(content().bytes(new byte[0]));
        mockMvc.perform(get(url).header(HttpHeaders.IF_MODIFIED_SINCE, lastModified))
                .andExpect(status().isNotModified())
                .andExpect(content().bytes(new byte[0]));
        mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=1-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-3/10"))
                .andExpect(content().bytes(new byte[]{0x50, 0x4E, 0x47}));
    }

    @Test
    void publicUrlsCannotReadFilesLinkedFromThePrivateVolume() throws Exception {
        Path publicRoot = Files.createDirectories(mediaRoot.resolve("uploads/public"));
        Path privateRoot = Files.createDirectories(mediaRoot.resolve("private"));
        Path privateFile = Files.write(privateRoot.resolve("secret.pdf"), PDF);
        Files.createSymbolicLink(publicRoot.resolve("linked-document.pdf"), privateFile);

        mockMvc.perform(get("/uploads/public/linked-document.pdf")).andExpect(status().isNotFound());
    }

    @Test
    void aDiskFailureIsDifferentFromAnAbsentPublicFile() throws Exception {
        Path publicRoot = Files.createDirectories(mediaRoot.resolve("uploads/public"));
        Files.write(publicRoot.resolve("failed-directory"), PNG);

        mockMvc.perform(get("/uploads/public/missing.png")).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/public/failed-directory/image.png"))
                .andExpect(status().isInternalServerError());
    }
}
