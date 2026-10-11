package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "Email vide")
    @Email(message = "Vous devez saisir un email  correct")
    private String email;

    /** Language of the email; defaults to French when absent. */
    @Pattern(regexp = "fr|en", message = "Langue non supportée")
    private String lang;
}
