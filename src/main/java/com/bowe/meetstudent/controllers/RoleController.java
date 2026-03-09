package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.RoleDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/roles")
@Tag(name = "02. Roles", description = "Endpoints for managing user access levels and permissions (Roles)")
public class RoleController {

    private final RoleService roleService;
    private final Mapper<Role, RoleDTO> roleMapper;

    @PostMapping
    @Operation(summary = "Create a new role", description = "Adds a new user role to the system.")
    @ApiResponse(responseCode = "201", description = "Role created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid role data")
    public ResponseEntity<RoleDTO> createRole(@RequestBody RoleDTO roleDTO) {
        Role roleEntity = roleMapper.toEntity(roleDTO);
        Role newRole = roleService.createRole(roleEntity);
        return new ResponseEntity<>(roleMapper.toDTO(newRole), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all roles", description = "Retrieves a list of all defined user roles.")
    @ApiResponse(responseCode = "200", description = "List of roles retrieved")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        List<RoleDTO> roles = roleService.findAllRoles()
                .stream()
                .map(roleMapper::toDTO)
                .toList();
        return new ResponseEntity<>(roles, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a role by ID", description = "Retrieves details of a specific role using its unique ID.")
    @ApiResponse(responseCode = "302", description = "Role found")
    @ApiResponse(responseCode = "404", description = "Role not found")
    public ResponseEntity<RoleDTO> getRoleById(
            @Parameter(description = "ID of the role to retrieve") @PathVariable Integer id) {
        return roleService.findRoleById(id)
                .map(role -> new ResponseEntity<>(roleMapper.toDTO(role), HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing role", description = "Performs a full update of a role's information.")
    @ApiResponse(responseCode = "200", description = "Role updated successfully")
    @ApiResponse(responseCode = "404", description = "Role not found")
    public ResponseEntity<RoleDTO> updateRole(
            @Parameter(description = "ID of the role to update") @PathVariable Integer id, 
            @RequestBody RoleDTO roleDetailsDTO) {
        try {
            Role roleDetailsEntity = roleMapper.toEntity(roleDetailsDTO);
            Role updatedRole = roleService.updateRole(id, roleDetailsEntity);
            return new ResponseEntity<>(roleMapper.toDTO(updatedRole), HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a role by ID", description = "Removes a role from the system using its unique ID.")
    @ApiResponse(responseCode = "204", description = "Role deleted successfully")
    @ApiResponse(responseCode = "404", description = "Role not found")
    public ResponseEntity<Void> deleteRole(
            @Parameter(description = "ID of the role to delete") @PathVariable Integer id) {
        roleService.deleteRole(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
