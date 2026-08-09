package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.RoleDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleMapper implements Mapper<Role, RoleDTO> {

    private final ModelMapper modelMapper;

    @Override
    public RoleDTO toDTO(Role role) {
        return modelMapper.map(role, RoleDTO.class);
    }

    @Override
    public Role toEntity(RoleDTO roleDTO) {
        return modelMapper.map(roleDTO, Role.class);
    }
}
