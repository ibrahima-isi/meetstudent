package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "media",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_media_owner_idempotency",
                columnNames = {"owner_id", "idempotency_key"}))
public class Media extends AbstractEntity {

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MediaCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private MediaVisibility visibility;

    @Column(name = "owner_id")
    private Integer ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 16)
    private VerificationStatus verificationStatus;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
}
