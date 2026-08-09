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
                .publicUrl(publicUrl(media))
                .build();
    }

    private String publicUrl(Media media) {
        if (media.getVisibility() == com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC
                && media.getStorageKey() != null) {
            return "/uploads/" + media.getStorageKey();
        }
        return null;
    }

    @Override
    public Media toEntity(MediaDTO mediaDTO) {
        throw new UnsupportedOperationException("Media are created through upload, not mapped from a DTO");
    }
}
