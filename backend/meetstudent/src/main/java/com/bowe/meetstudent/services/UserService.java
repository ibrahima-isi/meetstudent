package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.utils.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * UserService handle the logical part of the UserEntity
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void saveUser(UserEntity userEntity) {
        this.userRepository.save(userEntity);
    }

    public List<UserEntity> getAllUsers() {
        return this.userRepository.findAll();
    }

    public Optional<UserEntity> getUserById(int id) {
        return this.userRepository.findById(id);
    }

    public Optional<UserEntity> getUserByEmail(String email) {
        return this.userRepository.findByEmailIgnoreCase(email);
    }

    public boolean notExists(int id){
        return !this.userRepository.existsById(id);
    }

    public void deleteUser(int id) {
        this.userRepository.deleteById(id);
    }

    public List<UserEntity> getUsersByRole(Role role){
        return this.userRepository.findUserEntityByRole(role);
    }
}
