package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.utils.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/users")
public class UserController {

    private final UserService userService;
    private final Mapper<UserEntity, UserDTO> userMapper;


    @PostMapping
    public ResponseEntity<UserDTO> saveUser(@RequestBody UserDTO userDTO) {

        UserEntity userEntity = this.userMapper.toEntity(userDTO);
        this.userService.saveUser(userEntity);

        return new ResponseEntity<>(userDTO, HttpStatus.CREATED);
    }

    @GetMapping
    public List<UserDTO> getUsers() {

        return this.userService
                .getAllUsers()
                .stream()
                .map(userMapper::toDTO)
                .toList();
    }

    @GetMapping(path = "/id/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable int id) {

        Optional<UserEntity> user = this.userService.getUserById(id);
        return user.map( foundUser ->{
            UserDTO dto = this.userMapper.toDTO(foundUser);
            return new ResponseEntity<>(dto, HttpStatus.FOUND);
        })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/email/{email}")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email) {
        Optional<UserEntity> user = this.userService.getUserByEmail(email);

        return user.map( foundUser ->{
                    UserDTO dto = this.userMapper.toDTO(foundUser);
                    return new ResponseEntity<>(dto, HttpStatus.FOUND);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/role/{role}")
    public ResponseEntity<List<UserDTO>> getUserByRole(@PathVariable String role) {

        try {
            Role convertedRole = Role.valueOf(role.toUpperCase());
            List<UserDTO> userDTOS = this.userService
                    .getUsersByRole(convertedRole)
                    .stream()
                    .map(userMapper::toDTO)
                    .toList();

            return new ResponseEntity<>(userDTOS, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable int id, @RequestBody UserDTO userDTO) {

        if (userService.notExists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Optional<UserEntity> existingUserOptional = userService.getUserById(id);
        final UserEntity existingUser = setUserValueForPatch(userDTO, existingUserOptional);

        this.userService.saveUser(existingUser);

        UserDTO savedUser = this.userMapper.toDTO(existingUser);
        return new ResponseEntity<>(savedUser,HttpStatus.OK);
    }

    @PatchMapping(path = "{id}")
    public ResponseEntity<UserDTO> patchUser(@PathVariable int id, @RequestBody UserDTO userDTO) {

        Optional<UserEntity> existingUserOptional = userService.getUserById(id);

        if (existingUserOptional.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final UserEntity existingUser = setUserValueForPatch(userDTO, existingUserOptional);
        this.userService.saveUser(existingUser);

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

    /**
     * Get an existing from the Database orElse a new User
     * @param userDTO the user with the new values
     * @param existingUserOptional to update
     * @return UserEntity
     */
    private static UserEntity setUserValueForPatch(UserDTO userDTO, Optional<UserEntity> existingUserOptional) {
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
        if(userDTO.getSpeciality() != null){
            existingUser.setSpeciality(userDTO.getSpeciality());
        }
        if(userDTO.getRole() != null) {
            existingUser.setRole(userDTO.getRole());
        }
        if(userDTO.getBirthday() != null) {
            existingUser.setBirthday(userDTO.getBirthday());
        }
        return existingUser;
    }

    @DeleteMapping(path = "{id}")
    public ResponseEntity<?> deleteUser(@PathVariable int id) {

        if (userService.notExists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        this.userService.deleteUser(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
