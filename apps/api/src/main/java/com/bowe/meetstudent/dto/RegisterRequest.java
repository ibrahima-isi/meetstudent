package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.dto.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {

    @NotEmpty(message = "prenom vide")
    private String firstname;

    @NotEmpty(message = "Nom vide")
    private String lastname;

    @NotEmpty(message = "Email vide")
    @Email(message = "Vous devez saisir un email  correct")
    private String email;

    @NotEmpty(message = "mot de passe vide")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    @MaxUtf8Bytes(72)
    private String password;

    @NotEmpty(message = "Confirmez le mot de passe")
    private String confirmedPassword;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    private String qualification;

    /** Language of the verification email; defaults to French when absent. */
    @Pattern(regexp = "fr|en", message = "Langue non supportée")
    private String lang;
}
