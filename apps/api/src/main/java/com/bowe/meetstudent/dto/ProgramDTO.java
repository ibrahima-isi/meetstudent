package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ProgramDTO extends BaseDTO {
    private Integer duration;

    @Size(max = 1000, message = "description must be at most 1000 characters")
    @Pattern(regexp = "[^\\p{Cc}&&[^\\t\\r\\n]]*", message = "description contains forbidden characters")
    private String description;

    @Min(value = 0, message = "availableSeats must be 0 or more")
    @Max(value = 100000, message = "availableSeats must be at most 100000")
    private Integer availableSeats;

    @DecimalMin(value = "0", message = "annualFee must be 0 or more")
    @Digits(integer = 8, fraction = 2, message = "annualFee must have at most 8 digits and 2 decimals")
    private BigDecimal annualFee;

    @Pattern(regexp = "BTS_DUT|LICENCE|MASTER|DOCTORAT|AUTRE",
            message = "level must be one of BTS_DUT, LICENCE, MASTER, DOCTORAT, AUTRE")
    private String level;
    private Integer photoMediaId;
    private MediaDTO photo;
    private Integer schoolId;
    private String schoolName;
    private String schoolCode;
    private Double averageRate;

    private List<CourseDTO> courses;
}
