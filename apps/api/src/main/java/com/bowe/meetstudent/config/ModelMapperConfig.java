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

        // Credential version is server-owned; loose matching otherwise maps password into it.
        modelMapper.typeMap(com.bowe.meetstudent.dto.UserDTO.class, com.bowe.meetstudent.entities.UserEntity.class)
                .addMappings(mapping -> mapping.skip(com.bowe.meetstudent.entities.UserEntity::setPasswordVersion));
        return modelMapper;
    }
}