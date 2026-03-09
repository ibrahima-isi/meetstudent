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
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class School extends BaseEntity{

    private Date creation;

    @Embedded
    private Address address;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cover_photo_url")
    private String coverPhotoUrl;

    @OneToMany(mappedBy = "school", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Program> programs;

    @OneToMany(mappedBy = "school", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SchoolRate> schoolRates;

    @ManyToMany
    @JoinTable(
        name = "school_tags",
        joinColumns = @JoinColumn(name = "school_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags;

    public void addProgram(Program program) {
        if (programs == null) programs = new java.util.ArrayList<>();
        programs.add(program);
        program.setSchool(this);
    }

    public void removeProgram(Program program) {
        if (programs != null) {
            programs.remove(program);
            program.setSchool(null);
        }
    }
}
