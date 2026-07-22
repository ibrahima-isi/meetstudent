package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

/**
 * Self-service profile update payload. Deliberately excludes the role:
 * role changes go through the admin-only endpoint with {@link AdminUpdateUserRoleRequest}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateProfileRequest {

    private String firstname;

    private String lastname;

    @Email(message = "Vous devez saisir un email  correct")
    private String email;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    private String password;

    private String qualification;

    private List<String> diplomas;

    private List<String> certificates;

    private String presentationVideoUrl;
}
