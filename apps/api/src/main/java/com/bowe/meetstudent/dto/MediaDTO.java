package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaDTO {
    private Integer id;
    private MediaCategory category;
    private MediaVisibility visibility;
    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String publicUrl;
}
