package com.bowe.meetstudent.dto;

import jakarta.persistence.MappedSuperclass;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@ToString(callSuper = true)
@RequiredArgsConstructor
@SuperBuilder
@MappedSuperclass
public class BaseDTO extends AbstractDTO {

    private String code;

    private String name;
}
