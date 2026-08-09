package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AbstractEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserEntity user;
}
