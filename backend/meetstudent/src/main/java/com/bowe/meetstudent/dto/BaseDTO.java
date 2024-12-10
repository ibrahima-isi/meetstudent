package com.bowe.meetstudent.dto;

import jakarta.persistence.MappedSuperclass;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@SuperBuilder
@MappedSuperclass
public class BaseDTO {

    private Integer id;

    private String code;

    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt ;

    private Integer createdBy;

    private Integer modifiedBy;
}
