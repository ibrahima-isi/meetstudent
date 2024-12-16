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

    @ManyToOne
    @JoinColumn(name = "school_id")
    private School school;

    /**
     * Relation with the accreditations of this program
     */
    @OneToMany(mappedBy = "program")
    @ToString.Exclude
    private List<ProgramAccreditation> programAccreditations;

    @OneToMany(mappedBy = "program")
    private List<ProgramRate> programRates;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Program program = (Program) o;
        return getId() != null && Objects.equals(getId(), program.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
