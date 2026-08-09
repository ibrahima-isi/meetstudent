package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUpdateUserRoleRequest {

    @NotNull(message = "roleId requis")
    private Integer roleId;
}
