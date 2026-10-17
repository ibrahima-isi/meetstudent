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

    /** Control characters (NUL included, which PostgreSQL refuses) except tab and line breaks. */
    private static final String NO_CONTROL_CHARACTERS = "[^\\p{Cc}&&[^\\t\\r\\n]]*";

    @NotBlank(message = "Email vide")
    @Email(message = "Vous devez saisir un email correct")
    @Size(max = ContactMessage.MAX_EMAIL, message = "Email trop long (254 caractères maximum)")
    private String email;

    @NotBlank(message = "Sujet vide")
    @Size(max = ContactMessage.MAX_SUBJECT, message = "Sujet trop long (150 caractères maximum)")
    @Pattern(regexp = NO_CONTROL_CHARACTERS, message = "Sujet : caractères de contrôle interdits")
    private String subject;

    @NotBlank(message = "Message vide")
    @Size(max = ContactMessage.MAX_BODY, message = "Message trop long (5000 caractères maximum)")
    @Pattern(regexp = NO_CONTROL_CHARACTERS, message = "Message : caractères de contrôle interdits")
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

    /** Stripped (Unicode spaces included) before validation, so a text of spaces only is blank. */
    public void setSubject(String subject) {
        this.subject = subject == null ? null : subject.strip();
    }

    public void setMessage(String message) {
        this.message = message == null ? null : message.strip();
    }

    /** Never prints the visitor's address or text. */
    @Override
    public String toString() {
        return "ContactRequest[]";
    }
}
