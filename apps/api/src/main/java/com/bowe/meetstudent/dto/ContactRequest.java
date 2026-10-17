package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.ContactMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of {@code POST /api/v1/contact}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContactRequest {

    @NotBlank(message = "Email vide")
    @Email(message = "Vous devez saisir un email correct")
    @Size(max = ContactMessage.MAX_EMAIL, message = "Email trop long (254 caractères maximum)")
    private String email;

    @NotBlank(message = "Sujet vide")
    @Size(max = ContactMessage.MAX_SUBJECT, message = "Sujet trop long (150 caractères maximum)")
    private String subject;

    @NotBlank(message = "Message vide")
    @Size(max = ContactMessage.MAX_BODY, message = "Message trop long (5000 caractères maximum)")
    private String message;

    /** Language of the visitor ({@code fr} or {@code en}); optional. */
    @Pattern(regexp = "fr|en", message = "Langue non supportée")
    private String language;

    /** Honeypot: hidden in the form, so only a bot fills it. Deliberately not validated. */
    private String website;

    /** Surrounding spaces (a common paste artefact) are not part of the address. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    /** Never prints the visitor's address or text. */
    @Override
    public String toString() {
        return "ContactRequest[]";
    }
}
