package com.bowe.meetstudent.entities;

import jakarta.persistence.Column;
import com.bowe.meetstudent.entities.enums.ContactMailStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A message sent through the public contact form. Anonymous: no user reference, no IP address. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contact_messages", indexes = @Index(name = "idx_contact_messages_created_at", columnList = "created_at"))
public class ContactMessage {

    public static final int MAX_EMAIL = 254;
    public static final int MAX_SUBJECT = 150;
    public static final int MAX_BODY = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "sender_email", nullable = false, length = MAX_EMAIL)
    private String senderEmail;

    @Column(nullable = false, length = MAX_SUBJECT)
    private String subject;

    @Column(nullable = false, length = MAX_BODY)
    private String body;

    @Column(length = 2)
    private String language;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** State of the team mail; PENDING from the insert until the background delivery has finished. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "mail_status", nullable = false, length = 16)
    private ContactMailStatus mailStatus = ContactMailStatus.PENDING;

    /** Send attempts of the latest delivery run (0 until one has run, or when mail is disabled). */
    @Builder.Default
    @Column(name = "mail_attempts", nullable = false)
    private int mailAttempts = 0;

    @Column(name = "mail_sent_at")
    private Instant mailSentAt;

    /** Start of the current PENDING state set by a resend (V28); null means "since created_at". */
    @Column(name = "mail_pending_since")
    private Instant mailPendingSince;
}
