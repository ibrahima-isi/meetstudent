package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.services.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void createRole_shouldSaveRole() {
        Role role = Role.builder().name("ROLE_MANAGER").build();
        Role savedRole = Role.builder().id(10).name("ROLE_MANAGER").build();
        Mockito.when(roleRepository.save(role)).thenReturn(savedRole);

        Role result = roleService.createRole(role);

        assertEquals(savedRole, result);
    }

    @Test
    void updateRole_shouldUpdateExistingRole() {
        Role existingRole = Role.builder().id(10).name("ROLE_OLD").description("Old").build();
        Role updates = Role.builder().name("ROLE_NEW").description("New").build();
        Mockito.when(roleRepository.findById(10)).thenReturn(Optional.of(existingRole));
        Mockito.when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role result = roleService.updateRole(10, updates);

        assertEquals("ROLE_NEW", result.getName());
        assertEquals("New", result.getDescription());
    }

    @Test
    void updateRole_shouldFail_whenRoleDoesNotExist() {
        Mockito.when(roleRepository.findById(10)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> roleService.updateRole(10, Role.builder().name("ROLE_NEW").build()));

        Mockito.verify(roleRepository, Mockito.never()).save(any(Role.class));
    }

    @Test
    void deleteRole_shouldDeleteById() {
        roleService.deleteRole(10);

        Mockito.verify(roleRepository).deleteById(10);
    }
}
