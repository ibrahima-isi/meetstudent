package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.embedded.Address;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "schools")
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class School extends BaseEntity{

    private Date creation;

    @Embedded
    private Address address;

    @OneToMany(mappedBy = "school")
    @ToString.Exclude
    private List<Program> programs;

    @OneToMany(mappedBy = "school")
    @ToString.Exclude
    private List<SchoolRate> schoolRates;
}
