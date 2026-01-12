package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.utils.Utils;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Integer id;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

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
