package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UserRepository handle the DB part of the UserEntity
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);
    List<UserEntity> findByRole(Role role);
    List<UserEntity> findAllByPassword(String password);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Integer id);

    /** Targeted write: does not flush a whole loaded row, so it cannot overwrite a concurrent profile update. */
    @Modifying
    @Query("update UserEntity u set u.emailVerified = true where u.id = :id and lower(u.email) = lower(:email)")
    int markEmailVerified(@Param("id") Integer id, @Param("email") String email);
}
