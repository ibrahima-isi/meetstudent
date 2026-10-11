package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.enums.UserTokenPurpose;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A single-use account token (email verification, password reset). Only the SHA-256 hex of the
 * token is stored, never the token itself. {@code email} is the address it was issued for: the token
 * is refused if the user's address changed since.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "user_tokens", indexes = @Index(name = "idx_user_tokens_user_purpose_issued", columnList = "user_id, purpose, issued_at"))
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class UserToken extends AbstractEntity {

    /** Null once the account is deleted: the row stays as evidence for the per-address cap until the retention purge. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @ToString.Include
    private UserTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String email;

    /** Set from the injected Clock (not by JPA auditing) so cooldowns and expiry are testable. */
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
