package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.validation.SafeText;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Admin decisions on Expert accounts. The status is only ever written here, by a dedicated method. */
@Service
@RequiredArgsConstructor
public class AdminExpertService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_REASON_LENGTH = 255;
    private static final String EXPERT_ROLE = "ROLE_EXPERT";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final UserRepository users;

    /** Fixed order (registration date desc, then id desc): any requested sort is ignored, the size is capped. */
    @Transactional(readOnly = true)
    public Page<UserEntity> list(ExpertStatus status, Pageable requested) {
        var page = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE), NEWEST_FIRST);
        return status == null ? users.findExperts(page) : users.findExpertsByStatus(status, page);
    }

    /** @throws ResourceNotFoundException unknown user (404); @throws ConflictException not an Expert (409) */
    @Transactional
    public UserEntity approve(int id) {
        var expert = lockedExpert(id);
        decide(expert, ExpertStatus.APPROVED, null);
        return expert;
    }

    /** The reason is optional: trimmed, blank means none, at most 255 characters, no control characters. */
    @Transactional
    public UserEntity reject(int id, String reason) {
        var checked = checkedReason(reason);
        var expert = lockedExpert(id);
        decide(expert, ExpertStatus.REJECTED, checked);
        return expert;
    }

    private UserEntity lockedExpert(int id) {
        var user = users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() == null || !EXPERT_ROLE.equals(user.getRole().getName())) {
            throw new ConflictException("This account is not an Expert");
        }
        return user;
    }

    private void decide(UserEntity expert, ExpertStatus status, String reason) {
        expert.setExpertStatus(status);
        expert.setExpertStatusReason(reason);
        expert.setExpertDecisionDate(LocalDateTime.now());
    }

    private String checkedReason(String value) {
        if (value == null) return null;
        var reason = value.trim();
        if (reason.isEmpty()) return null;
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new FieldValidationException("reason", "Le motif ne doit pas dépasser 255 caractères");
        }
        if (SafeText.hasForbiddenCharacters(reason)) {
            throw new FieldValidationException("reason", "Le motif contient des caractères non autorisés");
        }
        return reason;
    }
}
