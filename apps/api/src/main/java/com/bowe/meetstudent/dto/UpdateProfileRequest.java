package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

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

    // Optional: null or empty means "leave the password unchanged" (see UserService.patch).
    @Pattern(regexp = "^(|.{8,})$", message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    private String qualification;
}
