package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.utils.ERatedEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "rates")
@Inheritance
@DiscriminatorColumn(name = "rated_entity")
public class Rate extends BaseEntity{

    private Double note;

    @Column(length = 150)
    private String comment;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity userEntity;



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Rate rate = (Rate) o;
        return getId() != null && Objects.equals(getId(), rate.getId());
    }

}
