package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.embedded.ProgramAccreditationId;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

/**
 * This is an associative class that represent the relation between a Program and his Accreditations
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "program_accreditations")
public class ProgramAccreditation {

    @EmbeddedId
    private ProgramAccreditationId id;

    @ManyToOne
    @MapsId("programId")
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @MapsId("accreditationId")
    @JoinColumn(name = "accreditation_id")
    private Accreditation accreditation;

    /**
     * The year that the accreditation has been given to the program
     */
    @Column(name = "starts")
    private Integer startsAt;

    /**
     * The year that the accreditation will end for the program
     */
    @Column(name = "ends")
    private Integer endsAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ProgramAccreditation that = (ProgramAccreditation) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}
