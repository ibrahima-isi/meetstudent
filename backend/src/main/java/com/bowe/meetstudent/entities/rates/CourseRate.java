package com.bowe.meetstudent.entities.rates;

import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Rate;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@DiscriminatorValue(value = "COURSE")
public class CourseRate extends Rate {

    @ManyToOne
    private Course course;
}
