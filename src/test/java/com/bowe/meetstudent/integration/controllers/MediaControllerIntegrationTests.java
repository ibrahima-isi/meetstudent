package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "file.upload-dir=target/test-uploads")
class MediaControllerIntegrationTests {

    private static final byte[] JPEG_CONTENT = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testThatAuthenticatedUserCanUploadValidJpeg() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.jpg", "image/jpeg", JPEG_CONTENT);

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/v1/media/schools/upload")
                        .file(file)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.url").isNotEmpty()
        );
    }

    @Test
    void testThatUploadRejectsDisallowedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", JPEG_CONTENT);

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/v1/media/schools/upload")
                        .file(file)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.error").isNotEmpty()
        );
    }

    @Test
    void testThatUploadRejectsContentNotMatchingDeclaredType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "definitely not a jpeg".getBytes());

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/v1/media/schools/upload")
                        .file(file)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        );
    }

    @Test
    void testThatUploadRejectsInvalidEntityType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.jpg", "image/jpeg", JPEG_CONTENT);

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/v1/media/unknown/upload")
                        .file(file)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        );
    }

    @Test
    void testThatAnonymousCannotUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.jpg", "image/jpeg", JPEG_CONTENT);

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/v1/media/schools/upload")
                        .file(file)
        ).andExpect(
                MockMvcResultMatchers.status().isUnauthorized()
        );
    }
}
