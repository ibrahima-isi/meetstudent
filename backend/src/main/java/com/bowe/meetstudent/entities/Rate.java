package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@ToString(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Rate extends AbstractEntity {

    private Double note;

    @Column(length = 150)
    private String comment;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity userEntity;
}