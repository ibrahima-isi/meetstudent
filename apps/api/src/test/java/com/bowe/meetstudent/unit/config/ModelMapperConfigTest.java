package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.ModelMapperConfig;
import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelMapperConfigTest {

    private final ModelMapper mapper = new ModelMapperConfig().modelMapper();

    @Test
    void aUserDtoWithASchoolWishlistMapsWithoutTouchingTheProgramWishlist() {
        var dto = UserDTO.builder().email("a@example.com")
                .wishlist(List.of(SchoolDTO.builder().name("School").build())).build();

        var entity = assertDoesNotThrow(() -> mapper.map(dto, UserEntity.class));

        assertTrue(entity.getProgramWishlist() == null || entity.getProgramWishlist().isEmpty());
    }

    @Test
    void serverOwnedApprovalFieldsAreNeverCopiedFromAUserDto() {
        var dto = UserDTO.builder().email("a@example.com").expertStatus(ExpertStatus.APPROVED)
                .expertStatusReason("self-approved").build();

        var entity = mapper.map(dto, UserEntity.class);

        assertNull(entity.getExpertStatus());
        assertNull(entity.getExpertStatusReason());
        assertNull(entity.getExpertDecisionDate());
        assertEquals("a@example.com", entity.getEmail());
    }
}
