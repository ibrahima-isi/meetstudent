package com.bowe.meetstudent.dto;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class BaseDTO {

    private Integer id;
    private String createdAt;
    private String updatedAt;
}
