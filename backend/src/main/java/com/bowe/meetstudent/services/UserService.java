package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.utils.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * UserService handle the logic of the UserEntity
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserEntity saveUser(UserEntity userEntity) {
        userEntity.setPassword(
                passwordEncoder.encode(
                        userEntity.getPassword()
                )
        );
        return this.userRepository.save(userEntity);
    }

    public List<UserEntity> findAllToList() {
        return this.userRepository.findAll();
    }

    public Page<UserEntity> findAll(Pageable pageable){
        return this.userRepository.findAll(pageable);
    }

    public Optional<UserEntity> getUserById(int id) {
        return this.userRepository.findById(id);
    }

    public Optional<UserEntity> getUserByEmail(String email) {
        return this.userRepository.findByEmailIgnoreCase(email);
    }

    public List<UserEntity> getUsersByRole(Role role){
        return this.userRepository.findUserEntityByRole(role);
    }

    public UserEntity deleteUser(int id) {
        UserEntity toDelete = this.userRepository.findById(id).orElse(null);
        this.userRepository.deleteById(id);

        return toDelete;
    }

    public boolean notExists(int id){
        return !this.userRepository.existsById(id);
    }


}
