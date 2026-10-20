package com.bowe.meetstudent.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {

        ModelMapper modelMapper = new ModelMapper();

        // This line will save one entity passed to another entity
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.LOOSE);
        modelMapper.getConfiguration().setSkipNullEnabled(true);

        // Credential version and expert approval are server-owned; loose matching otherwise maps password / createdAt into them.
        modelMapper.typeMap(com.bowe.meetstudent.dto.UserDTO.class, com.bowe.meetstudent.entities.UserEntity.class)
                .addMappings(mapping -> {
                    mapping.skip(com.bowe.meetstudent.entities.UserEntity::setPasswordVersion);
                    mapping.skip(com.bowe.meetstudent.entities.UserEntity::setExpertStatus);
                    mapping.skip(com.bowe.meetstudent.entities.UserEntity::setExpertStatusReason);
                    mapping.skip(com.bowe.meetstudent.entities.UserEntity::setExpertDecisionDate);
                    // UserDTO.wishlist holds schools; loose matching would otherwise bind it to the program wishlist.
                    mapping.skip(com.bowe.meetstudent.entities.UserEntity::setProgramWishlist);
                });
        return modelMapper;
    }
}