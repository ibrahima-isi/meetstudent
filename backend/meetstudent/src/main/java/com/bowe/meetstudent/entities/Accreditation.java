package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "accreditations")
@Data
public class Accreditation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private String code;

    private String name;

    private Integer yearOfAccreditation;

    @OneToMany(mappedBy = "accreditation")
    private List<ProgramAccreditation> programAccreditations;

}
