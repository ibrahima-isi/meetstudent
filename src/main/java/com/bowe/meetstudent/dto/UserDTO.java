package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserDTO extends AbstractDTO {

    @NotEmpty(message = "prenom vide")
    private String firstname;

    @NotEmpty(message = "Nom vide")
    private String lastname;

    @NotEmpty(message = "Email vide")
    @Email(message = "Vous devez saisir un email  correct")
    private String email;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthday;

    @NotEmpty(message = "mot de passe vide")
    private String password;

    @NotEmpty(message = "Confirmez le mot de passe")
    private String confirmedPassword;
    private Role role;
    private String qualification;
    private List<String> diplomas;
    private List<String> certificates;
    private String presentationVideoUrl;
    private List<SchoolDTO> wishlist;
    private String photoUrl;
}
