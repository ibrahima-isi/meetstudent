package com.bowe.meetstudent.repositories.auth;

import com.bowe.meetstudent.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthenticationRepository extends JpaRepository<UserEntity, Integer> {
    UserEntity findByEmail(String email);

}
