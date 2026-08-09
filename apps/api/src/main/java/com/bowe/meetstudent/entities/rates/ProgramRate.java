package com.bowe.meetstudent.entities.rates;

import com.bowe.meetstudent.entities.Program;
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
@Table(name = "program_rates")
public class ProgramRate extends Rate {

    @ManyToOne
    private Program program;
}
