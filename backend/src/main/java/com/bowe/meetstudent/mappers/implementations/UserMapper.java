package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.Mapper;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class UserMapper implements Mapper<UserEntity, UserDTO> {

    private final ModelMapper modelMapper;

    public UserMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }


    /**
     * Get a UserDTO from a UserEntity
     *
     * @param userEntity the user entity to map from
     * @return UserDTO that have been mapped
     */
    @Override
    public UserDTO toDTO(UserEntity userEntity) {
        UserDTO dto = modelMapper.map(userEntity, UserDTO.class);
        dto.setPassword(null);

        return dto;
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
