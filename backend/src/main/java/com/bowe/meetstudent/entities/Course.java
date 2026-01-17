package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.rates.CourseRate;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "courses")
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Course extends BaseEntity {

    private String code;

    @Column(name = "photo_url")
    private String photoUrl;

    @OneToMany(mappedBy = "course")
    private List<CourseRate> courseRates;
}
