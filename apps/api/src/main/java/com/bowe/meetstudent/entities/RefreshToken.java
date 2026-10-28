package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AbstractEntity {

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "password_version", nullable = false)
    private int passwordVersion;

    @Column(nullable = false, unique = true)
    private String token;

    /** SHA-256 of the secret this session's last rotation replaced: a replay of it reveals a stolen token (V37). */
    @Column(name = "previous_token")
    private String previousToken;

    /** When {@link #previousToken} was rotated away; starts the grace window for benign concurrent refreshes. */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(nullable = false)
    private Instant expiryDate;

    /** Stable across refresh-secret rotation; also carried by access JWTs as sid. */
    @Column(nullable = false, unique = true)
    private UUID sessionId;

    @Column(nullable = false)
    private Instant lastActivity;

    @Column(nullable = false)
    private boolean legacy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserEntity user;
}
