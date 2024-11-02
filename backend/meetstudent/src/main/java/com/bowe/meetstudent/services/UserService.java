package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.User;
import com.bowe.meetstudent.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void saveUser(User user) {
        this.userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return this.userRepository.findAll();
    }

    public Optional<User> getUserById(int id) {
        return this.userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return this.userRepository.findByEmail(email);
    }

    public void updateUser(User user) {
        this.userRepository.save(user);
    }

    public boolean exists(int id){
        return this.userRepository.existsById(id);
    }

    public void patchUser(User user) {
        this.userRepository.save(user);
    }

    public void deleteUser(int id) {
        this.userRepository.deleteById(id);
    }
}
