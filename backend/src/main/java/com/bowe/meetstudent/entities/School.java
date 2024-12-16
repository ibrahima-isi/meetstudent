package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.embedded.Address;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "schools")
public class School extends BaseEntity{

    private Date creation;

    @Embedded
    private Address address;

    @OneToMany(mappedBy = "school")
    @ToString.Exclude
    private List<Program> programs;

    @OneToMany(mappedBy = "school")
    @ToString.Exclude
    private List<SchoolRate> schoolRates;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        School school = (School) o;
        return getId() != null && Objects.equals(getId(), school.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
