package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.entities.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * UserService handle the logic of the UserEntity
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MediaService mediaService;

    public UserEntity saveUser(UserEntity userEntity, PasswordEncoder passwordEncoder ) {
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
        return this.userRepository.findByRole(role);
    }

    @Transactional
    public UserEntity deleteUser(int id) {
        Optional<UserEntity> toDeleteOpt = this.userRepository.findById(id);
        if (toDeleteOpt.isPresent()) {
            UserEntity toDelete = toDeleteOpt.get();
            // Delete diploma files if they exist
            if (toDelete.getDiplomas() != null) {
                toDelete.getDiplomas().forEach(mediaService::deleteMediaByUrl);
            }
            this.userRepository.deleteById(id);
            return toDelete;
        }
        return null;
    }

    public boolean notExists(int id){
        return !this.userRepository.existsById(id);
    }

    public boolean emailNotExists(String email){
        return !this.userRepository.existsByEmail(email);
    }

    public boolean isPasswordConfirmed(String password, String confirmedPassword) {
        return password != null && password.equals(confirmedPassword);
    }

    @Transactional
    public UserEntity patch(Integer id, UserEntity updates, PasswordEncoder encoder) {
        return userRepository.findById(id).map(existing -> {
            // Handle diploma changes
            if (updates.getDiplomas() != null) {
                mediaService.deleteRemovedMedia(existing.getDiplomas(), updates.getDiplomas());
                existing.setDiplomas(updates.getDiplomas());
            }
            
            // Map remaining fields
            if (updates.getFirstname() != null) existing.setFirstname(updates.getFirstname());
            if (updates.getLastname() != null) existing.setLastname(updates.getLastname());
            if (updates.getEmail() != null) existing.setEmail(updates.getEmail());
            if (updates.getBirthday() != null) existing.setBirthday(updates.getBirthday());
            if (updates.getQualification() != null) existing.setQualification(updates.getQualification());
            if (updates.getRole() != null) existing.setRole(updates.getRole());
            
            if (updates.getPassword() != null && !updates.getPassword().isEmpty()) {
                existing.setPassword(encoder.encode(updates.getPassword()));
            }
            
            return userRepository.save(existing);
        }).orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("User not found"));
    }

}
