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
public class CourseAccreditationId implements Serializable {
    private Integer courseId;
    private Integer accreditationId;
}
