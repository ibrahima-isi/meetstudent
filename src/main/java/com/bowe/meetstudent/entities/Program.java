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
    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<ProgramAccreditation> programAccreditations;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramRate> programRates;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Course> courses;

    public void addCourse(Course course) {
        if (courses == null) courses = new java.util.ArrayList<>();
        courses.add(course);
        course.setProgram(this);
    }

    public void removeCourse(Course course) {
        if (courses != null) {
            courses.remove(course);
            course.setProgram(null);
        }
    }
}
