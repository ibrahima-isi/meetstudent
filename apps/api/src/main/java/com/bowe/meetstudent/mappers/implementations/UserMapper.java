package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.MediaDTO;
import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserMapper implements Mapper<UserEntity, UserDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final MediaMapper mediaMapper;

    /**
     * Get a UserDTO from a UserEntity, enriched with the user's media (diplomas,
     * certificates, presentation video) resolved from the media table.
     *
     * @param userEntity the user entity to map from
     * @return UserDTO that have been mapped
     */
    @Override
    public UserDTO toDTO(UserEntity userEntity) {
        UserDTO dto = modelMapper.map(userEntity, UserDTO.class);
        dto.setPassword(null);
        dto.setConfirmedPassword(null);

        Integer ownerId = userEntity.getId();
        if (ownerId != null) {
            dto.setDiplomas(toMediaDTOs(ownerId, MediaCategory.DIPLOMA));
            dto.setCertificates(toMediaDTOs(ownerId, MediaCategory.CERTIFICATE));
            List<MediaDTO> videos = toMediaDTOs(ownerId, MediaCategory.PRESENTATION_VIDEO);
            dto.setPresentationVideo(videos.isEmpty() ? null : videos.get(0));
        }

        return dto;
    }

    private List<MediaDTO> toMediaDTOs(Integer ownerId, MediaCategory category) {
        return mediaService.findByOwnerIdAndCategory(ownerId, category).stream()
                .map(mediaMapper::toDTO)
                .toList();
    }

    /**
     * Get a UserEntity from a UserDTO
     *
     * @param userDTO the user DTO to map from
     * @return UserEntity that have been mapped
     */
    @Override
    public UserEntity toEntity(UserDTO userDTO) {
        return modelMapper.map(userDTO, UserEntity.class);
    }
}
