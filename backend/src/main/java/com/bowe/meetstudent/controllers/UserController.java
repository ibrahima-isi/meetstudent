package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.entities.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/users")
@Tag(name = "4. Users", description = "Endpoints for managing users")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder encoder;
    private final Mapper<UserEntity, UserDTO> userMapper;
    private final RoleService roleService;


    @PostMapping
    @Operation(summary = "Create a new user")
    @ApiResponse(responseCode = "201", description = "User created successfully")
    public ResponseEntity<UserDTO> saveUser(@RequestBody UserDTO userDTO) {

        UserEntity userEntity = this.userMapper.toEntity(userDTO);
        UserEntity savedUser = this.userService.saveUser(userEntity, encoder);
        UserDTO savedUserDto = this.userMapper.toDTO(savedUser);

        return new ResponseEntity<>(savedUserDto, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all users (paginated)")
    @ApiResponse(responseCode = "200", description = "List of all users")
    public Page<UserDTO> findAll() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("firstname").descending()); // 10 schools per page, sorted by name ascending
        Page<UserEntity> userEntities = this.userService.findAll(pageable);
        return userEntities.map(userMapper::toDTO);
    }

    @GetMapping(path = "/id/{id}")
    @Operation(summary = "Get a user by ID")
    @ApiResponse(responseCode = "302", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserDTO> findById(@Parameter(description = "ID of the user to retrieve") @PathVariable int id) {

        Optional<UserEntity> user = this.userService.getUserById(id);
        return user.map( foundUser ->{
            UserDTO dto = this.userMapper.toDTO(foundUser);
            return new ResponseEntity<>(dto, HttpStatus.FOUND);
        })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/email/{email}")
    @Operation(summary = "Get a user by email")
    @ApiResponse(responseCode = "302", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserDTO> findByEmail(@Parameter(description = "Email of the user to retrieve") @PathVariable String email) {
        Optional<UserEntity> user = this.userService.getUserByEmail(email);

        return user.map( foundUser ->{
                    UserDTO dto = this.userMapper.toDTO(foundUser);
                    return new ResponseEntity<>(dto, HttpStatus.FOUND);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/role/{role}")
    @Operation(summary = "Get users by role name")
    @ApiResponse(responseCode = "200", description = "List of users for the given role")
    @ApiResponse(responseCode = "400", description = "Role not found")
    public ResponseEntity<List<UserDTO>> findByRole(@Parameter(description = "Name of the role (e.g., STUDENT)") @PathVariable String role) {

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
    @Operation(summary = "Update a user by ID")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserDTO> updateUser(@Parameter(description = "ID of the user to update") @PathVariable int id, @RequestBody UserDTO userDTO) {

        if (userService.notExists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Optional<UserEntity> existingUserOptional = userService.getUserById(id);
        final UserEntity existingUser = setValuesForUpdate(userDTO, existingUserOptional);

        this.userService.saveUser(existingUser, encoder);
        UserDTO savedUser = this.userMapper.toDTO(existingUser);
        return new ResponseEntity<>(savedUser,HttpStatus.OK);
    }

    @PatchMapping(path = "{id}")
    @Operation(summary = "Partially update a user by ID")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "400", description = "Bad request")
    public ResponseEntity<UserDTO> patchUser(@Parameter(description = "ID of the user to patch") @PathVariable int id, @RequestBody UserDTO userDTO) {

        Optional<UserEntity> existingUserOptional = userService.getUserById(id);

        if (existingUserOptional.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final UserEntity existingUser = setValuesForUpdate(userDTO, existingUserOptional);
        this.userService.saveUser(existingUser, encoder);

        this.userService
                .getUserById(id)
                .map(
                        user -> {
                            UserDTO savedUser = userMapper.toDTO(user);
                            return new ResponseEntity<>(savedUser, HttpStatus.OK);
                        }
                    );

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @DeleteMapping(path = "{id}")
    @Operation(summary = "Delete a user by ID")
    @ApiResponse(responseCode = "200", description = "User deleted successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserDTO> deleteById(@Parameter(description = "ID of the user to delete") @PathVariable int id) {

        if (userService.notExists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        UserEntity deletedUser =  this.userService.deleteUser(id);
        UserDTO deletedUserDto = this.userMapper.toDTO(deletedUser);
        return new ResponseEntity<>(deletedUserDto, HttpStatus.OK);
    }

    /**
     * Get an existing from the Database orElse a new User
     * @param userDTO the user with the new values
     * @param existingUserOptional to update
     * @return UserEntity
     */
    private static UserEntity setValuesForUpdate(UserDTO userDTO, Optional<UserEntity> existingUserOptional) {
        UserEntity existingUser = existingUserOptional.orElseGet(UserEntity::new);

        if (userDTO.getFirstname() != null) {
            existingUser.setFirstname(userDTO.getFirstname());
        }
        if (userDTO.getLastname() != null) {
            existingUser.setLastname(userDTO.getLastname());
        }
        if (userDTO.getEmail() != null) {
            existingUser.setEmail(userDTO.getEmail());
        }
        if (userDTO.getPassword() != null) {
            existingUser.setPassword(userDTO.getPassword());
        }
        if(userDTO.getQualification() != null){
            existingUser.setQualification(userDTO.getQualification());
        }
        if(userDTO.getDiplomas() != null){
            existingUser.setDiplomas(userDTO.getDiplomas());
        }
        if(userDTO.getRole() != null) {
            existingUser.setRole(userDTO.getRole());
        }
        if(userDTO.getBirthday() != null) {
            existingUser.setBirthday(userDTO.getBirthday());
        }
        return existingUser;
    }


}
