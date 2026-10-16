package com.bowe.meetstudent.entities.rates;

import com.bowe.meetstudent.entities.Rate;
import com.bowe.meetstudent.entities.School;
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
@Table(name = "school_rates", uniqueConstraints = @UniqueConstraint(name = "uk_school_rates_user_school", columnNames = {"user_id", "school_id"}))
public class SchoolRate extends Rate {

    @ManyToOne
    private School school;
}
