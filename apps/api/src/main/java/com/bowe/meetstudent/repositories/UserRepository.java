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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Integer id);

    @Query("select u.passwordVersion from UserEntity u where u.id = :id")
    Optional<Integer> findPasswordVersionById(@Param("id") Integer id);

    Optional<UserEntity> findByEmailIgnoreCase(String email);
    List<UserEntity> findByRole(Role role);
    List<UserEntity> findAllByPassword(String password);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Integer id);

    /** Targeted write: does not flush a whole loaded row, so it cannot overwrite a concurrent profile update. */
    @Modifying
    @Query("update UserEntity u set u.emailVerified = true where u.id = :id and lower(u.email) = lower(:email)")
    int markEmailVerified(@Param("id") Integer id, @Param("email") String email);

    /** Targeted write of a confirmed new address (verified by definition: the link reached it), same reason as above. */
    @Modifying
    @Query("update UserEntity u set u.email = :email, u.emailVerified = true where u.id = :id")
    int changeEmail(@Param("id") Integer id, @Param("email") String email);

    /**
     * Every user whose address matches ignoring case, oldest first. A list, not an Optional: legacy data may
     * hold case-variant duplicates and a throwing lookup would turn into a 500 that reveals the address exists.
     */
    List<UserEntity> findAllByEmailIgnoreCaseOrderByIdAsc(String email);

    /** Targeted write of an already encoded password, for the same reason as {@link #markEmailVerified}. */
    @Modifying
    @Query("update UserEntity u set u.password = :password where u.id = :id")
    int updatePassword(@Param("id") Integer id, @Param("password") String password);
}
