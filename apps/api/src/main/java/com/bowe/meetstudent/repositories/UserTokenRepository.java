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

    @Query("select t.user.id from UserToken t where t.tokenHash = :hash")
    Optional<Integer> findUserIdByTokenHash(@Param("hash") String hash);

    Optional<UserToken> findByTokenHash(String tokenHash);

    /** Tokens issued to the user for the purpose since the given instant, oldest first. */
    List<UserToken> findByUserIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(Integer userId, UserTokenPurpose purpose, Instant since);

    /**
     * Tokens of one purpose issued for the address (any user, case-insensitive) since the instant, oldest first.
     * Explicit lower() so the lower(email), issued_at index (V18) is usable.
     */
    @Query("select t from UserToken t where lower(t.email) = lower(:email) and t.purpose = :purpose and t.issuedAt > :since order by t.issuedAt asc")
    List<UserToken> findAddressTokens(@Param("email") String email, @Param("purpose") UserTokenPurpose purpose, @Param("since") Instant since);

    /** The newest token of the purpose that is still open (not consumed, not expired). */
    Optional<UserToken> findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(
            Integer userId, UserTokenPurpose purpose, Instant now);

    /** Global retention purge, orphaned rows (deleted users) included. */
    @Modifying(flushAutomatically = true)
    @Query("delete from UserToken t where t.issuedAt < :before")
    int deleteIssuedBefore(@Param("before") Instant before);

    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.consumedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.consumedAt is null")
    int consumeOpen(@Param("userId") Integer userId, @Param("purpose") UserTokenPurpose purpose, @Param("now") Instant now);

    /** Atomic compare-and-set: returns 0 when another request consumed the token first. */
    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.consumedAt = :now where t.id = :id and t.consumedAt is null")
    int consumeIfOpen(@Param("id") Integer id, @Param("now") Instant now);

}
