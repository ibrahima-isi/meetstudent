package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.embedded.ProgramAccreditationId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "program_accreditations")
public class ProgramAccreditation {

    @EmbeddedId
    private ProgramAccreditationId id;

    @ManyToOne
    @MapsId("programId")
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @MapsId("accreditationId")
    @JoinColumn(name = "accreditation_id")
    private Accreditation accreditation;

    @Column(name = "starts")
    private Integer yearOfAccreditation;

    private Integer duration;
}
