package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "programs")
@Entity
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(name = "code")
    private String code;

    private String name;

    private Integer duration;

    @ManyToOne
    @JoinColumn(name = "school_id")
    private School school;

    /**
     * Relation with the accreditations of this program
     */
    @OneToMany(mappedBy = "program")
    private List<ProgramAccreditation> programAccreditations;
}
