package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.utils.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
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
public class UserDTO {

    private Integer id;

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
    private String speciality;
}
