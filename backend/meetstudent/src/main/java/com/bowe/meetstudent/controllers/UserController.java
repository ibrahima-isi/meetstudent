package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.entities.UserEntity;
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

    @PostMapping
    public ResponseEntity<UserEntity> saveUser(@RequestBody UserEntity userEntity) {
        this.userService.saveUser(userEntity);
        return new ResponseEntity<>(userEntity, HttpStatus.CREATED);
    }

    @GetMapping
    public List<UserEntity> getUsers() {
        return this.userService.getAllUsers();
    }

    @GetMapping(path = "/id/{id}")
    public ResponseEntity<UserEntity> getUserById(@PathVariable int id) {
        Optional<UserEntity> user = this.userService.getUserById(id);

        return user.map(foundUser -> new ResponseEntity<>(foundUser, HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/email/{email}")
    public ResponseEntity<UserEntity> getUserByEmail(@PathVariable String email) {
        Optional<UserEntity> user = this.userService.getUserByEmail(email);

        return user.map(foundUser -> new ResponseEntity<>(foundUser, HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/role/{role}")
    public ResponseEntity<List<UserEntity>> getUserByRole(@PathVariable String role) {
        try {
            Role convertedRole = Role.valueOf(role.toUpperCase());
            List<UserEntity> users = this.userService.getUsersByRole(convertedRole);
            return new ResponseEntity<>(users, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<UserEntity> updateUser(@PathVariable int id, @RequestBody UserEntity userEntity) {

        if (userService.notExists(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        userEntity.setId(id);
        this.userService.updateUser(userEntity);
        return ResponseEntity.ok(userEntity);
    }

    @PatchMapping(path = "{id}")
    public ResponseEntity<UserEntity> patchUser(@PathVariable int id, @RequestBody UserEntity userEntity) {

        Optional<UserEntity> existingUserOptional = userService.getUserById(id);
        if (existingUserOptional.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        UserEntity existingUserEntity = existingUserOptional.get();

        if (userEntity.getFirstname() != null) {
            existingUserEntity.setFirstname(userEntity.getFirstname());
        }
        if (userEntity.getLastname() != null) {
            existingUserEntity.setLastname(userEntity.getLastname());
        }
        if (userEntity.getEmail() != null) {
            existingUserEntity.setEmail(userEntity.getEmail());
        }
        if (userEntity.getPassword() != null) {
            existingUserEntity.setPassword(userEntity.getPassword());
        }

        userService.updateUser(existingUserEntity);
        return new ResponseEntity<>(existingUserEntity, HttpStatus.OK);
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
