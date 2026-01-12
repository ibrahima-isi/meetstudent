package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.embedded.Address;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;
import java.util.List;

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

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cover_photo_url")
    private String coverPhotoUrl;

    @OneToMany(mappedBy = "school")
    private List<Program> programs;

    @OneToMany(mappedBy = "school")
    private List<SchoolRate> schoolRates;
}
