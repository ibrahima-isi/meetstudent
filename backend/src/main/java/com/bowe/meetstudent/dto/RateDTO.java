package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.*;
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
public class RateDTO extends AbstractDTO {

    @NotNull(message = "Note is required")
    @DecimalMin(value = "0.0", message = "Rating must be at least 0")
    @DecimalMax(value = "5.0", message = "Rating must be at most 5")
    private Double note;

    @Size(max = 500, message = "Comment cannot exceed 500 characters")
    private String comment;

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Integer userId;
}
