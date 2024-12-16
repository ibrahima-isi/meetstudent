package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.utils.Utils;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 5, unique = true)
    private String code;

    @Column(length = 50)
    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt ;

    private Integer createdBy;

    private Integer modifiedBy;

    @PrePersist
    protected void onCreate() {
        setCreatedAt(
                Utils.dakarTimeZone()
        );
        setModifiedAt(
                Utils.dakarTimeZone()
        );
    }

    @PreUpdate
    protected void onUpdate() {

        this.setModifiedAt(
                Utils.dakarTimeZone()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        BaseEntity that = (BaseEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
