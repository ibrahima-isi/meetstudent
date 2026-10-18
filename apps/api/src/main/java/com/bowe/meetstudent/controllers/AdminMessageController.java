package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.AdminMessageDTO;
import com.bowe.meetstudent.services.AdminMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Messages received through the public contact form. ADMIN only (see {@code /api/v1/admin/**} in WebSecurityConfig). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/messages")
@Tag(name = "13. Admin messages", description = "Contact messages received through the public form (ADMIN only)")
public class AdminMessageController {

    private final AdminMessageService service;

    @GetMapping
    @Operation(summary = "List contact messages (paginated)",
            description = "Newest first, then id descending. The order is fixed (?sort= is ignored) and the page size is capped at 100.")
    @ApiResponse(responseCode = "200", description = "Page of messages")
    public Page<AdminMessageDTO> list(@ParameterObject Pageable pageable) {
        return service.list(pageable).map(m -> new AdminMessageDTO(m.getId(), m.getSenderEmail(), m.getSubject(),
                m.getBody(), m.getLanguage(), m.getCreatedAt(), m.getMailStatus().name(), m.getMailAttempts(),
                m.getMailSentAt()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a contact message")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Message not found")
    public void delete(@PathVariable int id) {
        service.delete(id);
    }
}
