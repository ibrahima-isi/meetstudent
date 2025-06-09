package com.bowe.meetstudent.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginDTO {

    @NotEmpty(message = "Saisissez un email")
    @Email(message = "Respecter le format du mail standard")
    private String email;

    @NotEmpty(message = "Saisissez un mot de passe")
    private String password;
}
