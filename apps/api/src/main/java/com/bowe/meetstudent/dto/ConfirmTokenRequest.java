package com.bowe.meetstudent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmTokenRequest {

    @NotBlank(message = "Lien invalide ou expiré")
    @Size(max = 128, message = "Lien invalide ou expiré")
    private String token;
}
