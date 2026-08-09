package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;
import org.modelmapper.internal.bytebuddy.build.HashCodeAndEqualsPlugin;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "accreditations")
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Accreditation extends BaseEntity {


    private String description;

    @OneToMany(mappedBy = "accreditation")
    private List<ProgramAccreditation> programAccreditations;
}
