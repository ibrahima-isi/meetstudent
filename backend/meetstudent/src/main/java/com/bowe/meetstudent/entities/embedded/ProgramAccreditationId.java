package com.bowe.meetstudent.entities.embedded;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProgramAccreditationId implements Serializable {

    private Integer programId;
    private Integer accreditationId;
}
