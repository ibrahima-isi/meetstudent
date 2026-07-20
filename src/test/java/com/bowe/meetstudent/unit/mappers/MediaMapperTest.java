package com.bowe.meetstudent.unit.mappers;

import com.bowe.meetstudent.dto.MediaDTO;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import com.bowe.meetstudent.mappers.implementations.MediaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MediaMapperTest {

    private final MediaMapper mapper = new MediaMapper();

    @Test
    void toDtoCopiesSafeFieldsAndOmitsStorageKey() {
        Media media = Media.builder()
                .storageKey("private/secret.pdf")
                .originalFilename("diploma.pdf")
                .contentType("application/pdf")
                .sizeBytes(1234L)
                .category(MediaCategory.DIPLOMA)
                .visibility(MediaVisibility.PRIVATE)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(media, "id", 42);

        MediaDTO dto = mapper.toDTO(media);

        assertEquals(42, dto.getId());
        assertEquals("diploma.pdf", dto.getOriginalFilename());
        assertEquals(MediaCategory.DIPLOMA, dto.getCategory());
        assertEquals(MediaVisibility.PRIVATE, dto.getVisibility());
        assertEquals(VerificationStatus.PENDING, dto.getVerificationStatus());
        assertEquals(1234L, dto.getSizeBytes());
    }

    @Test
    void toEntityIsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> mapper.toEntity(new MediaDTO()));
    }
}
