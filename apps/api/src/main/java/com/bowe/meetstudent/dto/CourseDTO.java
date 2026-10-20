package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class CourseDTO extends BaseDTO {
    @Size(max = 1000, message = "description must be at most 1000 characters")
    @Pattern(regexp = "[^\\p{Cc}&&[^\\t\\r\\n]]*", message = "description must not contain control characters")
    private String description;
    private Integer photoMediaId;
    private MediaDTO photo;
    private Integer programId;
    private String programName;
    private Double averageRate;
}
