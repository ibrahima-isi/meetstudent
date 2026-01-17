package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Integer id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime modifiedAt;

    @CreatedBy
    private Integer createdBy;

    @LastModifiedBy
    private Integer modifiedBy;
}
