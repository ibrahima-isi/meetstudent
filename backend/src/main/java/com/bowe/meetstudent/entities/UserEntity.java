package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.utils.Role;
import com.bowe.meetstudent.utils.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * This class represent a User of the platform which can be an Admin, a Student or an Expert who can evaluate the content
 * @author ibrabowe97
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "users")

public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 100)
    private String firstname;

    @Column(length = 50)
    private String lastname;

    private Date birthday;

    @Column(unique = true)
    private String email;

    private String password;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(length = 100)
    private String speciality;

    /**
     * relationship with the user's diploma
     */
    @ManyToMany(mappedBy = "users")
    @ToString.Exclude
    private List<Diploma> diplomas;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        UserEntity that = (UserEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }

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
