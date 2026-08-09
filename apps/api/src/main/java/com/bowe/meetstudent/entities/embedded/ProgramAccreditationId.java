package com.bowe.meetstudent.entities.embedded;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * This an ambeddable composite primary key for the Relationship between Programs and Accreditations
 * @implNote This class implements Serializable to allow it to be used as a composite key in JPA.
 *           Ensure that both programId and accreditationId are properly set to avoid issues with entity relationships.
 * @author ibrabowe97
 */
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProgramAccreditationId implements Serializable {

    private Integer programId;
    private Integer accreditationId;
}
