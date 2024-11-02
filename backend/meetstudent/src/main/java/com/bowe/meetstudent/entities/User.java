package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.utils.UserType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_role")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(length = 100)
    private String firstname;

    @Column(length = 50)
    private String lastname;

    @Column(unique = true)
    private String email;

//    @JsonIgnore
    private String password;

    @Enumerated
    private UserType userType;
}
