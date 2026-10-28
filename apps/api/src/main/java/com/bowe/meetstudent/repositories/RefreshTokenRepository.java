package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findBySessionIdAndUserId(UUID sessionId, Integer userId);

    @Query("select r from RefreshToken r where r.user.id = :userId and r.legacy = true order by r.id desc")
    List<RefreshToken> findLegacySessions(@Param("userId") Integer userId);

    /** Owner of a token without loading the user, so the user row can be locked before it is read. */
    @Query("select r.user.id from RefreshToken r where r.token = :token")
    Optional<Integer> findUserIdByToken(@Param("token") String token);

    /** The session whose last rotation replaced this secret hash (reuse detection). */
    Optional<RefreshToken> findByPreviousToken(String previousToken);

    /** Owner of a rotated-away secret, read before the user row is locked like {@link #findUserIdByToken}. */
    @Query("select r.user.id from RefreshToken r where r.previousToken = :token")
    Optional<Integer> findUserIdByPreviousToken(@Param("token") String token);

    /** Bulk delete of one session, whatever its current secret: a rotation may have replaced it meanwhile. */
    @Modifying
    @Query("delete from RefreshToken r where r.sessionId = :sessionId and r.user.id = :userId")
    int deleteBySessionIdAndUserId(@Param("sessionId") UUID sessionId, @Param("userId") Integer userId);

    /** Bulk delete: a derived delete loads and versions each row and fails when a concurrent refresh touched it. */
    @Modifying
    @Query("delete from RefreshToken r where r.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Integer userId);
}
