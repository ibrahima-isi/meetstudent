package com.bowe.meetstudent.entities.rates;

import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.Rate;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
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
@Table(name = "program_rates", uniqueConstraints = @UniqueConstraint(name = "uk_program_rates_user_program", columnNames = {"user_id", "program_id"}))
public class ProgramRate extends Rate {

    @ManyToOne(optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;
}
