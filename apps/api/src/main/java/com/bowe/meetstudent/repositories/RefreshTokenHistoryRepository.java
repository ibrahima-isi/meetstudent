package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.RefreshTokenHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenHistoryRepository extends JpaRepository<RefreshTokenHistory, Integer> {

    Optional<RefreshTokenHistory> findByTokenHash(String tokenHash);

    /** Owner of a rotated-away secret, read before the user row is locked like {@link RefreshTokenRepository#findUserIdByToken}. */
    @Query("select h.refreshToken.user.id from RefreshTokenHistory h where h.tokenHash = :tokenHash")
    Optional<Integer> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    /** The most recent rotation of a session (ids grow with each rotation, all made under the owner's lock). */
    @Query("select max(h.id) from RefreshTokenHistory h where h.refreshToken.id = :refreshTokenId")
    Optional<Integer> findLatestId(@Param("refreshTokenId") Integer refreshTokenId);

    /** Forgets the user's rotated secrets older than {@code cutoff}. Callers hold the user's row lock. */
    @Modifying
    @Query("delete from RefreshTokenHistory h where h.rotatedAt < :cutoff "
            + "and h.refreshToken.id in (select r.id from RefreshToken r where r.user.id = :userId)")
    int purgeRotatedBefore(@Param("userId") Integer userId, @Param("cutoff") Instant cutoff);
}
