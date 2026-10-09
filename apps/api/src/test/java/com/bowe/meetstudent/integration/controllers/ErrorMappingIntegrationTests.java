package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorMappingIntegrationTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void uploadWithDisallowedExtensionIs415() throws Exception {
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3}))
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("File extension is not allowed"));
    }

    @Test
    void uploadWithMimeNotMatchingExtensionIs415() throws Exception {
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "doc.pdf", "image/png", new byte[]{'%', 'P', 'D', 'F', 1, 2}))
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadWithContentNotMatchingDeclaredTypeIs415() throws Exception {
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "not a pdf".getBytes()))
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadOfEmptyFileIs400() throws Exception {
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[0]))
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unknownRouteIs404() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist").with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "d.pdf", "application/pdf",
                new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', ' '});
    }

    @Test
    void invalidCategoryIs400NamingAllowedValues() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf()).param("category", "NOPE")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("DIPLOMA")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SCHOOL_LOGO")));
    }

    @Test
    void missingFilePartIs400() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("file")));
    }

    @Test
    void missingCategoryIs400() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("category")));
    }

    @Test
    void jsonBodyOnUploadIs415() throws Exception {
        mockMvc.perform(post("/api/v1/media?category=DIPLOMA").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void nonNumericSchoolIdIs400() throws Exception {
        mockMvc.perform(get("/api/v1/schools/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("id")));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/api/v1/schools").contentType(MediaType.APPLICATION_JSON)
                        .content("{not json").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void emptyBodyIs400() throws Exception {
        mockMvc.perform(post("/api/v1/schools").contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void textPlainBodyIs415() throws Exception {
        mockMvc.perform(post("/api/v1/schools").contentType(MediaType.TEXT_PLAIN)
                        .content("hello").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void putOnAuthIs405() throws Exception {
        mockMvc.perform(put("/api/v1/auth"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void deleteOnSchoolsCollectionIs405() throws Exception {
        mockMvc.perform(delete("/api/v1/schools").with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
