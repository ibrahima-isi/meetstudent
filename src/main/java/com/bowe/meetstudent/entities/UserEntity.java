package com.bowe.meetstudent.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class UserEntity extends AbstractEntity {

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

    @ManyToOne
    @JoinColumn(name = "role_id")
    @ToString.Include
    private Role role;

    @Column(length = 100)
    private String qualification;

    @Column
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.ARRAY)
    private List<String> diplomas;
}
