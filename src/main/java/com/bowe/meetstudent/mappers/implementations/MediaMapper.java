package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.MediaDTO;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.mappers.Mapper;
import org.springframework.stereotype.Component;

@Component
public class MediaMapper implements Mapper<Media, MediaDTO> {

    @Override
    public MediaDTO toDTO(Media media) {
        return MediaDTO.builder()
                .id(media.getId())
                .category(media.getCategory())
                .visibility(media.getVisibility())
                .verificationStatus(media.getVerificationStatus())
                .rejectionReason(media.getRejectionReason())
                .originalFilename(media.getOriginalFilename())
                .contentType(media.getContentType())
                .sizeBytes(media.getSizeBytes())
                .build();
    }

    @Override
    public Media toEntity(MediaDTO mediaDTO) {
        throw new UnsupportedOperationException("Media are created through upload, not mapped from a DTO");
    }
}
