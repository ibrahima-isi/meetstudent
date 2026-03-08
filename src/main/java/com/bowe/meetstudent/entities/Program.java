package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.rates.ProgramRate;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "programs")
public class Program extends BaseEntity{

    private Integer duration;

    @Column(name = "photo_url")
    private String photoUrl;

    @ManyToOne
    @JoinColumn(name = "school_id")
    private School school;

    /**
     * Relation with the accreditations of this program
     */
    @OneToMany(mappedBy = "program")
    @ToString.Exclude
    private List<ProgramAccreditation> programAccreditations;

    @OneToMany(mappedBy = "program")
    private List<ProgramRate> programRates;
}
