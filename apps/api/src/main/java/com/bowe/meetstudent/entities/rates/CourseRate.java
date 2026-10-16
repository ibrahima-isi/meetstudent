package com.bowe.meetstudent.entities.rates;

import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Rate;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@SuperBuilder
@AllArgsConstructor
@Entity
@Table(name = "course_rates", uniqueConstraints = @UniqueConstraint(name = "uk_course_rates_user_course", columnNames = {"user_id", "course_id"}))
public class CourseRate extends Rate {

    @ManyToOne
    private Course course;
}
