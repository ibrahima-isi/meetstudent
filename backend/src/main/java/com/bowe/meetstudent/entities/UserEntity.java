package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

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
@Builder
@Entity
@Table(name = "users")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Integer id;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @ManyToOne
    @JoinColumn(name = "role_id")
    @ToString.Include
    private Role role;

    @Column(length = 100)
    private String speciality;

    /**
     * relationship with the user's diploma
     */
    @ManyToMany(mappedBy = "users")
    private List<Diploma> diplomas;

    /**
     * Run before each Creation of entity
     */
    @PrePersist
    protected void onCreate() {
        setCreatedAt(
                Utils.dakarTimeZone()
        );
        setModifiedAt(
                Utils.dakarTimeZone()
        );
    }

    /**
     * Run before each Update of an entity orElse creation.
     */
    @PreUpdate
    protected void onUpdate() {

        this.setModifiedAt(
                Utils.dakarTimeZone()
        );
    }
}
