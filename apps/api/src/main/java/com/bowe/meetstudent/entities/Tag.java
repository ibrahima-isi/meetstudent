package com.bowe.meetstudent.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "tags")
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Tag extends AbstractEntity {

    @Column(nullable = false, unique = true)
    @ToString.Include
    private String name;

    @ManyToMany(mappedBy = "tags")
    private List<School> schools;
}
