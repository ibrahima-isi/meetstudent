package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.UserToken;
import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Integer> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    /** Tokens issued to the user for the purpose since the given instant, oldest first. */
    List<UserToken> findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(Integer userId, UserTokenPurpose purpose, Instant since);

    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.consumedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.consumedAt is null")
    int consumeOpen(@Param("userId") Integer userId, @Param("purpose") UserTokenPurpose purpose, @Param("now") Instant now);

    /** Atomic compare-and-set: returns 0 when another request consumed the token first. */
    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.consumedAt = :now where t.id = :id and t.consumedAt is null")
    int consumeIfOpen(@Param("id") Integer id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserToken t where t.user.id = :userId and t.issuedAt < :before")
    int deleteIssuedBefore(@Param("userId") Integer userId, @Param("before") Instant before);
}
