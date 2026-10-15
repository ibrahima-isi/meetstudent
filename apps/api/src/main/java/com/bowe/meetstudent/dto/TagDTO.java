package com.bowe.meetstudent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class TagDTO extends AbstractDTO {
    @NotBlank(message = "Tag name is required")
    @Size(max = 255, message = "Tag name must not exceed 255 characters")
    private String name;
}
