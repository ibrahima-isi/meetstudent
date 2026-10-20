package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.validation.SafeText;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.mail.EmailType;
import com.bowe.meetstudent.mail.ExpertEmailRequested;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    /** Fixed order (registration date desc, then id desc): any requested sort is ignored, the size is capped. */
    @Transactional(readOnly = true)
    public Page<UserEntity> list(ExpertStatus status, Pageable requested) {
        var page = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE), NEWEST_FIRST);
        return status == null ? users.findExperts(page) : users.findExpertsByStatus(status, page);
    }

    /**
     * Idempotent: approving an already approved Expert changes nothing (date kept) and sends no mail.
     * The mail goes out after commit, in {@code lang} (fr or en, French by default).
     *
     * @throws ResourceNotFoundException unknown user (404); @throws ConflictException not an Expert (409)
     */
    @Transactional
    public UserEntity approve(int id, String lang) {
        var expert = lockedExpert(id);
        if (decide(expert, ExpertStatus.APPROVED, null)) {
            notify(expert, EmailType.EXPERT_APPROVED, lang, null);
        }
        return expert;
    }

    /**
     * The reason is optional: trimmed, blank means none, at most 255 characters, no control characters. Idempotent
     * like {@link #approve}: rejecting an already rejected Expert keeps the first reason and date and sends no mail.
     */
    @Transactional
    public UserEntity reject(int id, String reason, String lang) {
        var checked = checkedReason(reason);
        var expert = lockedExpert(id);
        if (decide(expert, ExpertStatus.REJECTED, checked)) {
            notify(expert, EmailType.EXPERT_REJECTED, lang, checked);
        }
        return expert;
    }

    private UserEntity lockedExpert(int id) {
        var user = users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() == null || !EXPERT_ROLE.equals(user.getRole().getName())) {
            throw new ConflictException("This account is not an Expert");
        }
        return user;
    }

    /** @return true when the status really changed (only then are the date and the mail written) */
    private boolean decide(UserEntity expert, ExpertStatus status, String reason) {
        if (expert.getExpertStatus() == status) {
            return false;
        }
        expert.setExpertStatus(status);
        expert.setExpertStatusReason(reason);
        expert.setExpertDecisionDate(LocalDateTime.now());
        return true;
    }

    private void notify(UserEntity expert, EmailType type, String lang, String reason) {
        events.publishEvent(new ExpertEmailRequested(type, expert.getId(), expert.getEmail(), expert.getFirstname(), lang, reason));
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
