package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.enums.ExpertStatus;

import java.time.LocalDateTime;

/** An Expert account as the back office queue sees it: no credential, token or other internal field. */
public record AdminExpertDTO(Integer id, String firstname, String lastname, String email, String qualification,
                             LocalDateTime registeredAt, ExpertStatus status, String reason,
                             LocalDateTime decisionDate) {
}
