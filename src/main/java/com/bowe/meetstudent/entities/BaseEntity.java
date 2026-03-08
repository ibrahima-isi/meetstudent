package com.bowe.meetstudent.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class BaseEntity extends AbstractEntity {

    @Column(length = 5, unique = true)
    @ToString.Include
    private String code;

    @Column(length = 50)
    @ToString.Include
    private String name;
}
