package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A refresh secret that a session rotated away (only its SHA-256, like {@link RefreshToken#getToken()}), kept for
 * the 7-day inactivity window so that a replay reveals a copied token (V37). Deleted with its session.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "refresh_token_history")
public class RefreshTokenHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refresh_token_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RefreshToken refreshToken;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;
}
