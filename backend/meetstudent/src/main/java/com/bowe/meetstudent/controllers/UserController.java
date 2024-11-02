package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.entities.User;
import com.bowe.meetstudent.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(path = "/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<User> saveUser(@RequestBody User user){
        this.userService.saveUser(user);
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public List<User> getUsers(){
        return this.userService.getAllUsers();
    }

    @GetMapping(path = "/id/{id}")
    public ResponseEntity<User> getUserById(@PathVariable int id){
        Optional<User> user = this.userService.getUserById(id);

        return user.map(foundUser -> new ResponseEntity<>(foundUser, HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(path = "/email/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email){
        Optional<User> user = this.userService.getUserByEmail(email);

        return user.map(foundUser -> new ResponseEntity<>(foundUser, HttpStatus.FOUND))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<User> updateUser(@PathVariable int id, @RequestBody User user){

        if( !userService.exists(id) ){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        user.setId(id);
        this.userService.updateUser(user);
        return ResponseEntity.ok(user);
    }

    @PatchMapping(path = "{id}")
public ResponseEntity<User> patchUser(@PathVariable int id, @RequestBody User user) {

    Optional<User> existingUserOptional = userService.getUserById(id);
    if (existingUserOptional.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    User existingUser = existingUserOptional.get();

    if (user.getFirstname() != null) {
        existingUser.setFirstname(user.getFirstname());
    }
    if(user.getLastname()!= null){
        existingUser.setLastname(user.getLastname());
    }
    if (user.getEmail() != null) {
        existingUser.setEmail(user.getEmail());
    }
    if(user.getPassword() != null){
        existingUser.setPassword(user.getPassword());
    }
    if(user.getUserType() != null){
        existingUser.setUserType(user.getUserType());
    }

    userService.updateUser(existingUser);
    return ResponseEntity.ok(existingUser);
}


    @DeleteMapping(path = "{id}")
    public ResponseEntity<?> deleteUser(@PathVariable int id){

        if( !userService.exists(id) ){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        this.userService.deleteUser(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
