package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

/**
 * Diploma Represent a graduation document of Student
 * @author ibrabowe97
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Entity
public class Diploma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String name;
    private Date yearOf;

    @ManyToMany
    @JoinTable(
            joinColumns = @JoinColumn(name = "diploma_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    private List<UserEntity> users;

}
