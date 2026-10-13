package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByToken(String token);

    /** Owner of a token without loading the user, so the user row can be locked before it is read. */
    @Query("select r.user.id from RefreshToken r where r.token = :token")
    Optional<Integer> findUserIdByToken(@Param("token") String token);

    /** Bulk delete: a derived delete loads and versions each row and fails when a concurrent refresh touched it. */
    @Modifying
    @Query("delete from RefreshToken r where r.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Integer userId);
}
