package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.AdminExpertDTO;
import com.bowe.meetstudent.dto.RejectExpertRequest;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.services.AdminExpertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Approval queue of Expert accounts. ADMIN only (see {@code /api/v1/admin/**} in WebSecurityConfig). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/experts")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "14. Admin experts", description = "Approval queue of Expert (teacher) accounts (ADMIN only)")
public class AdminExpertController {

    private final AdminExpertService service;

    @GetMapping
    @Operation(summary = "List Expert accounts (paginated)",
            description = "Newest registration first, then id descending. The order is fixed (?sort= is ignored) and the "
                    + "page size is capped at 100. Optional ?status=PENDING|APPROVED|REJECTED.")
    @ApiResponse(responseCode = "200", description = "Page of experts")
    @ApiResponse(responseCode = "400", description = "Unknown status")
    public Page<AdminExpertDTO> list(@RequestParam(required = false) ExpertStatus status,
                                     @ParameterObject Pageable pageable) {
        return service.list(status, pageable).map(AdminExpertController::toDto);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an Expert (also re-approves a rejected one); clears the reason")
    @ApiResponse(responseCode = "200", description = "The expert after the decision")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "The account is not an Expert")
    public AdminExpertDTO approve(@PathVariable int id) {
        return toDto(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject an Expert (also valid for an approved one) with an optional reason",
            description = "Body {\"reason\": \"...\"} is optional: trimmed, blank means none, 255 characters at most, "
                    + "no control characters (400 {\"reason\": ...}). Ratings already published are kept.")
    @ApiResponse(responseCode = "200", description = "The expert after the decision")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "The account is not an Expert")
    public AdminExpertDTO reject(@PathVariable int id, @RequestBody(required = false) RejectExpertRequest request) {
        return toDto(service.reject(id, request == null ? null : request.reason()));
    }

    private static AdminExpertDTO toDto(UserEntity u) {
        return new AdminExpertDTO(u.getId(), u.getFirstname(), u.getLastname(), u.getEmail(), u.getQualification(),
                u.getCreatedAt(), u.getExpertStatus(), u.getExpertStatusReason(), u.getExpertDecisionDate());
    }
}
