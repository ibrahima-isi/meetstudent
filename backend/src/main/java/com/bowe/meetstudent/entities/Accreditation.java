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
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Accreditation extends BaseEntity{

    @OneToMany(mappedBy = "accreditation")
    private List<ProgramAccreditation> programAccreditations;
}
