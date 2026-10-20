package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.SchoolRateRepository;
import com.bowe.meetstudent.repositories.ProgramRateRepository;
import com.bowe.meetstudent.repositories.CourseRateRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private static final String EXPERT_ROLE = "ROLE_EXPERT";

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final RoleRepository roleRepository;
    private final MediaService mediaService;
    private final UserTokenService userTokenService;
    private final RefreshTokenService refreshTokenService;
    private final SchoolRateRepository schoolRateRepository;
    private final ProgramRateRepository programRateRepository;
    private final CourseRateRepository courseRateRepository;

    public UserEntity saveUser(UserEntity userEntity, PasswordEncoder passwordEncoder ) {
        userEntity.setPassword(
                passwordEncoder.encode(
                        userEntity.getPassword()
                )
        );
        return this.userRepository.save(userEntity);
    }

    public UserEntity registerPublic(UserEntity userEntity, String requestedRole, PasswordEncoder passwordEncoder) {
        String roleName = requestedRole == null ? DEFAULT_REGISTRATION_ROLE : requestedRole;
        if (!DEFAULT_REGISTRATION_ROLE.equals(roleName) && !EXPERT_ROLE.equals(roleName)) {
            throw new FieldValidationException("role", "Choisissez un rôle étudiant ou enseignant");
        }
        String qualification = checkedQualification(userEntity.getQualification());
        if (EXPERT_ROLE.equals(roleName) && (qualification == null || qualification.isBlank())) {
            throw new FieldValidationException("qualification", "Indiquez votre spécialité");
        }
        userEntity.setQualification(qualification);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Registration role not found"));
        userEntity.setRole(role);
        // A teacher starts pending: the Expert rights come only once an admin approves the account.
        userEntity.setExpertStatus(EXPERT_ROLE.equals(roleName)
                ? ExpertStatus.PENDING : null);
        return saveUser(userEntity, passwordEncoder);
    }

    /**
     * An admin who makes someone an Expert approves them at the same time; any other role carries no status.
     * Keeping the same role leaves an existing decision untouched.
     */
    private void applyAdminRole(UserEntity user, Role newRole) {
        boolean wasExpert = user.getRole() != null && EXPERT_ROLE.equals(user.getRole().getName());
        boolean isExpert = EXPERT_ROLE.equals(newRole.getName());
        user.setRole(newRole);
        if (isExpert && !wasExpert) {
            user.setExpertStatus(ExpertStatus.APPROVED);
            user.setExpertStatusReason(null);
            user.setExpertDecisionDate(LocalDateTime.now());
        } else if (!isExpert) {
            user.setExpertStatus(null);
            user.setExpertStatusReason(null);
            user.setExpertDecisionDate(null);
        }
    }

    private String checkedQualification(String value) {
        if (value == null) return null;
        String qualification = value.trim();
        if (qualification.length() > 100) {
            throw new FieldValidationException("qualification", "La spécialité ne doit pas dépasser 100 caractères");
        }
        return qualification;
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
    public void deleteUser(int id, PasswordEncoder encoder, String currentPassword,
                           boolean administratorDeletingAnotherUser) {
        var user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!administratorDeletingAnotherUser
                && (currentPassword == null || !encoder.matches(currentPassword, user.getPassword()))) {
            throw new FieldValidationException("currentPassword", "Mot de passe actuel incorrect ou manquant");
        }
        deleteAccountData(user);
    }

    private void deleteAccountData(UserEntity user) {
        var id = user.getId();
        // The caller holds the user lock, also used by login/rotation and action-token consumption.
        refreshTokenService.deleteByUserId(id);
        schoolRateRepository.deleteAllByUserId(id);
        programRateRepository.deleteAllByUserId(id);
        courseRateRepository.deleteAllByUserId(id);
        mediaService.deleteAllOwnedBy(id);
        // JPA removes the owned wishlist links. Action-token FKs become NULL: their rows keep
        // counting toward the address budget until the existing seven-day retention purge.
        userRepository.delete(user);
    }

    public boolean notExists(int id){
        return !this.userRepository.existsById(id);
    }

    public boolean emailNotExists(String email){
        return !this.userRepository.existsByEmailIgnoreCase(email);
    }

    public boolean isPasswordConfirmed(String password, String confirmedPassword) {
        return password != null && password.equals(confirmedPassword);
    }

    @Transactional
    public UserEntity patch(Integer id, UserEntity updates, PasswordEncoder encoder) {
        return patch(id, updates, encoder, false, null, false);
    }

    @Transactional
    public UserEntity patchAsAdmin(Integer id, UserEntity updates, PasswordEncoder encoder) {
        return patch(id, updates, encoder, true, null, false);
    }

    @Transactional
    public UserEntity patch(Integer id, UserEntity updates, PasswordEncoder encoder,
                            String currentPassword, boolean administratorEditingAnotherUser) {
        return patch(id, updates, encoder, false, currentPassword, administratorEditingAnotherUser);
    }

    private UserEntity patch(Integer id, UserEntity updates, PasswordEncoder encoder, boolean allowRoleUpdate,
                             String currentPassword, boolean administratorEditingAnotherUser) {
        return userRepository.findByIdForUpdate(id).map(existing -> {
            if (updates.getPassword() != null && !updates.getPassword().isEmpty()
                    && !administratorEditingAnotherUser
                    && (currentPassword == null || !encoder.matches(currentPassword, existing.getPassword()))) {
                throw new FieldValidationException("currentPassword", "Mot de passe actuel incorrect ou manquant");
            }
            // The address changes only through EmailChangeService (current password + a link to the new address).
            if (updates.getEmail() != null && !updates.getEmail().equalsIgnoreCase(existing.getEmail())) {
                throw new FieldValidationException("email",
                        "Pour changer d'adresse e-mail, utilisez la demande de changement d'adresse");
            }
            // Profile fields only. Media (diplomas, certificates, videos, photo) are managed
            // through MediaController and owned via the media table, not through the user patch.
            if (updates.getFirstname() != null) existing.setFirstname(updates.getFirstname());
            if (updates.getLastname() != null) existing.setLastname(updates.getLastname());
            if (updates.getBirthday() != null) existing.setBirthday(updates.getBirthday());
            if (updates.getQualification() != null) existing.setQualification(checkedQualification(updates.getQualification()));
            if (allowRoleUpdate && updates.getRole() != null) applyAdminRole(existing, updates.getRole());

            if (updates.getPassword() != null && !updates.getPassword().isEmpty()) {
                existing.setPassword(encoder.encode(updates.getPassword()));
                existing.setPasswordVersion(Math.incrementExact(existing.getPasswordVersion()));
                refreshTokenService.deleteByUserId(id);
                // A password chosen here makes any emailed reset link stale.
                userTokenService.consumeOutstanding(existing.getId(), UserTokenPurpose.PASSWORD_RESET);
                userTokenService.consumeOutstanding(existing.getId(), UserTokenPurpose.EMAIL_CHANGE);
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
