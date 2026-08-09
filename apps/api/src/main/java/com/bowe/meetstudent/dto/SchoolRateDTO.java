package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class SchoolRateDTO extends RateDTO {

    @NotNull(message = "School ID is required")
    @Positive(message = "School ID must be positive")
    private Integer schoolId;
}
