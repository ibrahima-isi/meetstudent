package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.dto.validation.MaxUtf8Bytes;
import com.bowe.meetstudent.dto.validation.SafeName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @Size(max = 100, message = "Prénom trop long (100 caractères maximum)")
    @SafeName(message = "Prénom : caractères non autorisés")
    private String firstname;

    private String lastname;

    @Email(message = "Vous devez saisir un email  correct")
    private String email;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    // Optional: null or empty means "leave the password unchanged" (see UserService.patch).
    @Pattern(regexp = "^(|.{8,})$", message = "Le mot de passe doit contenir au moins 8 caractères")
    @MaxUtf8Bytes(72)
    private String password;

    private String currentPassword;

    private String qualification;
}
