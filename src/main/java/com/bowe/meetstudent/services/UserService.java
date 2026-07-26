package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.entities.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/**
 * UserService handle the logic of the UserEntity
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_REGISTRATION_ROLE = "ROLE_STUDENT";

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final RoleRepository roleRepository;
    private final MediaService mediaService;

    public UserEntity saveUser(UserEntity userEntity, PasswordEncoder passwordEncoder ) {
        userEntity.setPassword(
                passwordEncoder.encode(
                        userEntity.getPassword()
                )
        );
        return this.userRepository.save(userEntity);
    }

    public UserEntity registerStudent(UserEntity userEntity, PasswordEncoder passwordEncoder) {
        Role studentRole = roleRepository.findByName(DEFAULT_REGISTRATION_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Default student role not found"));
        userEntity.setRole(studentRole);
        return saveUser(userEntity, passwordEncoder);
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
            // Delete all media (diplomas, certificates, videos, photo) owned by this user
            mediaService.deleteAllOwnedBy(id);

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
        return patch(id, updates, encoder, false);
    }

    @Transactional
    public UserEntity patchAsAdmin(Integer id, UserEntity updates, PasswordEncoder encoder) {
        return patch(id, updates, encoder, true);
    }

    private UserEntity patch(Integer id, UserEntity updates, PasswordEncoder encoder, boolean allowRoleUpdate) {
        return userRepository.findById(id).map(existing -> {
            // Profile fields only. Media (diplomas, certificates, videos, photo) are managed
            // through MediaController and owned via the media table, not through the user patch.
            if (updates.getFirstname() != null) existing.setFirstname(updates.getFirstname());
            if (updates.getLastname() != null) existing.setLastname(updates.getLastname());
            if (updates.getEmail() != null) existing.setEmail(updates.getEmail());
            if (updates.getBirthday() != null) existing.setBirthday(updates.getBirthday());
            if (updates.getQualification() != null) existing.setQualification(updates.getQualification());
            if (allowRoleUpdate && updates.getRole() != null) existing.setRole(updates.getRole());

            if (updates.getPassword() != null && !updates.getPassword().isEmpty()) {
                existing.setPassword(encoder.encode(updates.getPassword()));
            }
            
            return userRepository.save(existing);
        }).orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("User not found"));
    }

    public UserEntity resolveAuthenticatedUser(Integer authenticatedUserId, Integer requestedUserId) {
        if (authenticatedUserId == null) {
            throw new AccessDeniedException("Authentication is required.");
        }
        if (requestedUserId != null && !requestedUserId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("You cannot act on behalf of another user.");
        }
        return userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    @Transactional
    public UserEntity addToWishlist(Integer userId, Integer schoolId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("User not found"));
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("School not found"));
        
        if (user.getWishlist() == null) {
            user.setWishlist(new ArrayList<>());
        }
        
        if (!user.getWishlist().contains(school)) {
            user.getWishlist().add(school);
        }
        
        return userRepository.save(user);
    }

    @Transactional
    public UserEntity removeFromWishlist(Integer userId, Integer schoolId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new com.bowe.meetstudent.exceptions.ResourceNotFoundException("User not found"));
        
        if (user.getWishlist() != null) {
            user.getWishlist().removeIf(s -> s.getId().equals(schoolId));
        }
        
        return userRepository.save(user);
    }

}
