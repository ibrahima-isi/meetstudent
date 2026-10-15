package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.embedded.CourseAccreditationId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "course_accreditations")
public class CourseAccreditation {
    @EmbeddedId
    private CourseAccreditationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("courseId")
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("accreditationId")
    @JoinColumn(name = "accreditation_id")
    private Accreditation accreditation;

    @Column(name = "starts")
    private Integer startsAt;

    @Column(name = "ends")
    private Integer endsAt;
}
