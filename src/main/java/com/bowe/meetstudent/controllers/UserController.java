package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/users")
@Tag(name = "4. Users", description = "Endpoints for managing user accounts (Admins, Students, Experts)")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder encoder;
    private final Mapper<UserEntity, UserDTO> userMapper;
    private final RoleService roleService;
    private final ModelMapper modelMapper;

    @PostMapping
    @Operation(summary = "Create a new user", description = "Registers a new user in the system with a specified role.")
    @ApiResponse(responseCode = "201", description = "User created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input data or email already exists")
    public ResponseEntity<UserDTO> saveUser(@RequestBody UserDTO userDTO) {
        UserEntity userEntity = this.userMapper.toEntity(userDTO);
        UserEntity savedUser = this.userService.saveUser(userEntity, encoder);
        UserDTO savedUserDto = this.userMapper.toDTO(savedUser);
        return new ResponseEntity<>(savedUserDto, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all users (paginated)", description = "Retrieves a paginated list of all users registered on the platform.")
    @ApiResponse(responseCode = "200", description = "List of users retrieved")
    public Page<UserDTO> findAll(@ParameterObject Pageable pageable) {
        Page<UserEntity> userEntities = this.userService.findAll(pageable);
        return userEntities.map(userMapper::toDTO);
    }

    @GetMapping(path = "/id/{id}")
    @Operation(summary = "Get a user by ID", description = "Retrieves detailed information about a specific user by their unique ID.")
    @ApiResponse(responseCode = "302", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserDTO> findById(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to retrieve") @PathVariable int id) {
        
        checkOwnershipOrAdmin(principal, id);
        
        Optional<UserEntity> user = this.userService.getUserById(id);
        return user.map( foundUser ->{
            UserDTO dto = this.userMapper.toDTO(foundUser);
            return new ResponseEntity<>(dto, HttpStatus.FOUND);
        }).orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/email/{email}")
    @Operation(summary = "Get a user by email", description = "Retrieves user details using their unique email address.")
    public ResponseEntity<UserDTO> findByEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Email of the user to retrieve") @PathVariable String email) {
        
        Optional<UserEntity> user = this.userService.getUserByEmail(email);
        if (user.isPresent()) {
            checkOwnershipOrAdmin(principal, user.get().getId());
            return new ResponseEntity<>(this.userMapper.toDTO(user.get()), HttpStatus.FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping(path = "/role/{role}")
    @Operation(summary = "Get users by role name", description = "Retrieves a list of all users assigned to a specific role (e.g., STUDENT).")
    public ResponseEntity<List<UserDTO>> findByRole(
            @Parameter(description = "Name of the role (e.g., STUDENT)") @PathVariable String role) {
        Optional<Role> roleOptional = roleService.findRoleByName(role.toUpperCase());
        if (roleOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<UserDTO> userDTOS = this.userService
                .getUsersByRole(roleOptional.get())
                .stream()
                .map(userMapper::toDTO)
                .toList();
        return new ResponseEntity<>(userDTOS, HttpStatus.OK);
    }

    @PutMapping(path = "/{id}")
    @Operation(summary = "Update a user by ID", description = "Performs a full update of an existing user's information.")
    public ResponseEntity<UserDTO> updateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to update") @PathVariable int id, 
            @RequestBody UserDTO userDTO) {
        
        checkOwnershipOrAdmin(principal, id);
        
        UserEntity updates = userMapper.toEntity(userDTO);
        UserEntity saved = this.userService.patch(id, updates, encoder);
        return ResponseEntity.ok(userMapper.toDTO(saved));
    }

    @PatchMapping(path = "{id}")
    @Operation(summary = "Partially update a user by ID", description = "Updates only the specific fields provided for a user.")
    public ResponseEntity<UserDTO> patchUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to patch") @PathVariable int id, 
            @RequestBody UserDTO userDTO) {
        
        checkOwnershipOrAdmin(principal, id);
        
        UserEntity updates = userMapper.toEntity(userDTO);
        UserEntity saved = this.userService.patch(id, updates, encoder);
        return ResponseEntity.ok(userMapper.toDTO(saved));
    }

    @DeleteMapping(path = "{id}")
    @Operation(summary = "Delete a user by ID", description = "Removes a user account from the platform.")
    public ResponseEntity<UserDTO> deleteById(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID of the user to delete") @PathVariable int id) {
        
        checkOwnershipOrAdmin(principal, id);
        
        UserEntity deletedUser =  this.userService.deleteUser(id);
        if (deletedUser == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        UserDTO deletedUserDto = this.userMapper.toDTO(deletedUser);
        return new ResponseEntity<>(deletedUserDto, HttpStatus.OK);
    }

    @PostMapping("/{userId}/wishlist/{schoolId}")
    @Operation(summary = "Add a school to user wishlist", description = "Adds a specific school to the user's personal wishlist.")
    public ResponseEntity<UserDTO> addToWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer userId, 
            @PathVariable Integer schoolId) {
        
        checkOwnershipOrAdmin(principal, userId);
        UserEntity user = userService.addToWishlist(userId, schoolId);
        return ResponseEntity.ok(userMapper.toDTO(user));
    }

    @DeleteMapping("/{userId}/wishlist/{schoolId}")
    @Operation(summary = "Remove a school from user wishlist", description = "Removes a specific school from the user's wishlist.")
    public ResponseEntity<UserDTO> removeFromWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer userId, 
            @PathVariable Integer schoolId) {
        
        checkOwnershipOrAdmin(principal, userId);
        UserEntity user = userService.removeFromWishlist(userId, schoolId);
        return ResponseEntity.ok(userMapper.toDTO(user));
    }

    private void checkOwnershipOrAdmin(UserPrincipal principal, Integer userId) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !principal.getId().equals(userId)) {
            throw new AccessDeniedException("You can only access or modify your own profile.");
        }
    }
}
