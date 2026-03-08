package com.bowe.meetstudent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProgramAccreditationDTO {
    private Integer programId;
    private Integer accreditationId;
    private AccreditationDTO accreditation;
    private Integer startsAt;
    private Integer endsAt;
}
