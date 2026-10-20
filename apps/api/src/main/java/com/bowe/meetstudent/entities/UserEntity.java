package com.bowe.meetstudent.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * This class represent a User of the platform which can be an Admin, a Student or an Expert who can evaluate the content
 * @author ibrabowe97
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "users")
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class UserEntity extends AbstractEntity {

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "password_version", nullable = false)
    private int passwordVersion;

    @Column(length = 100)
    @ToString.Include
    private String firstname;

    @Column(length = 50)
    @ToString.Include
    private String lastname;

    @ToString.Include
    private Date birthday;

    @Column(unique = true)
    @ToString.Include
    private String email;

    @JsonIgnore
    private String password;

    /** Optional: unverified accounts can sign in. Set once the user opens the link sent to {@code email}. */
    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @ManyToOne
    @JoinColumn(name = "role_id")
    @ToString.Include
    private Role role;

    @Column(length = 100)
    private String qualification;

    /** Approval status of an Expert; null for every other role. */
    @Enumerated(EnumType.STRING)
    @Column(name = "expert_status", length = 10)
    private ExpertStatus expertStatus;

    /** Optional reason given by the admin when rejecting (max 255). */
    @Column(name = "expert_status_reason", length = 255)
    private String expertStatusReason;

    @Column(name = "expert_decision_date")
    private LocalDateTime expertDecisionDate;

    @ManyToMany
    @JoinTable(
        name = "user_wishlist",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "school_id")
    )
    private List<School> wishlist;

    @ManyToMany
    @JoinTable(
        name = "user_program_wishlist",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "program_id")
    )
    @JsonIgnore
    private List<Program> programWishlist;
}
