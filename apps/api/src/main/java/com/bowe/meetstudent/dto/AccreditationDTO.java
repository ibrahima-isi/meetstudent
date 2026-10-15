package com.bowe.meetstudent.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;
import jakarta.validation.constraints.Size;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AccreditationDTO extends BaseDTO {
    @Size(max = 255, message = "description must be at most 255 characters")
    private String description;
}
