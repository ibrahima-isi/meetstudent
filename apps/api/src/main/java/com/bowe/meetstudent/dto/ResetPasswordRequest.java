package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Lien invalide ou expiré")
    @Size(max = 128, message = "Lien invalide ou expiré")
    private String token;

    @NotEmpty(message = "Le mot de passe doit contenir au moins 8 caractères")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    @NotEmpty(message = "Les mots de passe ne correspondent pas")
    private String confirmedPassword;
}
