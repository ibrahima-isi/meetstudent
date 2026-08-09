package com.bowe.meetstudent.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class CourseDTO extends BaseDTO {
    private Integer photoMediaId;
    private MediaDTO photo;
    private Integer programId;
    private Double averageRate;
}
