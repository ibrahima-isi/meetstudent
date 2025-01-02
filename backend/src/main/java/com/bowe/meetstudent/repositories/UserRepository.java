package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.utils.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UserRepository handle the DB part of the UserEntity
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);
    List<UserEntity> findUserEntityByRole(Role role);
}
