package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.enums.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaVerificationRequest {

    @NotNull(message = "status requis")
    private VerificationStatus status;

    @Size(max = 500, message = "Le motif ne peut dépasser 500 caractères")
    private String reason;
}
