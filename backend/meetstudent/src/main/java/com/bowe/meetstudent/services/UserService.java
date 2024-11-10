package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
        return this.userRepository.findByEmail(email);
    }

    public void updateUser(UserEntity userEntity) {
        this.userRepository.save(userEntity);
    }

    public boolean notExists(int id){
        return !this.userRepository.existsById(id);
    }

    public void patchUser(UserEntity userEntity) {
        this.userRepository.save(userEntity);
    }

    public void deleteUser(int id) {
        this.userRepository.deleteById(id);
    }
}
