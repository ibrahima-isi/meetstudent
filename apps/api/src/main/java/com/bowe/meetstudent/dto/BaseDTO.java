package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.dto.validation.OnCreate;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@MappedSuperclass
public class BaseDTO extends AbstractDTO {

    @Size(max = 5, message = "code must be at most 5 characters")
    private String code;

    @NotBlank(groups = OnCreate.class, message = "name is required")
    @Size(max = 50, message = "name must be at most 50 characters")
    private String name;
}
