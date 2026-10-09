package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.exceptions.MediaValidationException;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Each upload validation failure maps to the HTTP status a client can act on. */
@ExtendWith(MockitoExtension.class)
class MediaServiceValidationTest {

    @Mock MediaStorageService storageService;
    @Mock MediaRepository mediaRepository;
    @InjectMocks MediaService mediaService;

    private final UserPrincipal student = UserPrincipal.builder().id(7).username("u@x.com")
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))).build();

    private HttpStatus statusOf(MockMultipartFile file) {
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", 8L);
        return assertThrows(MediaValidationException.class,
                () -> mediaService.upload(file, MediaCategory.DIPLOMA, student, null)).getStatus();
    }

    @Test
    void emptyFileIsBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0])));
    }

    @Test
    void oversizedFileIsPayloadTooLarge() {
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE,
                statusOf(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[9])));
    }

    @Test
    void missingExtensionIsBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(new MockMultipartFile("file", "noext", "application/pdf", new byte[4])));
    }

    @Test
    void disallowedExtensionIsUnsupportedMediaType() {
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                statusOf(new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[4])));
    }

    @Test
    void mimeMismatchIsUnsupportedMediaType() {
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                statusOf(new MockMultipartFile("file", "a.pdf", "image/png", new byte[4])));
    }
}
