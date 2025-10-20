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
@RequiredArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(length = 5, unique = true)
    @ToString.Include
    private String code;

    @Column(length = 50)
    @ToString.Include
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
}
