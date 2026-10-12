package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.dto.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Asks to move the account to a new address; the current password proves it is the owner asking. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailChangeRequest {

    @NotBlank(message = "L'adresse e-mail est obligatoire")
    @Email(message = "Vous devez saisir un email correct")
    @Size(max = 255, message = "L'adresse e-mail est trop longue")
    private String newEmail;

    @NotBlank(message = "Le mot de passe actuel est obligatoire")
    @MaxUtf8Bytes(72)
    private String currentPassword;

    /** Language of the confirmation email; defaults to French when absent. */
    @Pattern(regexp = "fr|en", message = "Langue non supportée")
    private String lang;
}
