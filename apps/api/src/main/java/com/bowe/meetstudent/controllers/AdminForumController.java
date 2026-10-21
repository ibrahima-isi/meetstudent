package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.ForumReplyDTO;
import com.bowe.meetstudent.dto.ForumReportDTO;
import com.bowe.meetstudent.dto.ForumThreadDTO;
import com.bowe.meetstudent.entities.ForumThread;
import com.bowe.meetstudent.entities.enums.ForumReportStatus;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.ForumModerationService;
import com.bowe.meetstudent.services.ForumReportService;
import com.bowe.meetstudent.services.ForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Forum moderation: the reports queue, hiding, locking and deleting. ADMIN only (also guarded by
 * {@code /api/v1/admin/**} in WebSecurityConfig). Hide/unhide, lock/unlock and closing a report are idempotent; a second delete answers 404. The text of a message is never edited.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/forum")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "17. Admin forum", description = "Forum reports and moderation (ADMIN only)")
public class AdminForumController {

    private final ForumReportService reports;
    private final ForumModerationService moderation;
    private final ForumService forum;

    // ---- reports -------------------------------------------------------------------------------

    @GetMapping("/reports")
    @Operation(summary = "List the reports (paginated)",
            description = "Newest first, then id descending; the order is fixed (?sort= is ignored) and the page size is "
                    + "capped at 100. Optional ?status=OPEN|HANDLED|DISMISSED (all when absent). Each report names its "
                    + "target (THREAD or REPLY, ids, thread title, an excerpt of 200 characters at most, whether it is "
                    + "hidden), the reason and the reporter's first name (no e-mail; absent once the account is deleted).")
    @ApiResponse(responseCode = "200", description = "Page of reports")
    @ApiResponse(responseCode = "400", description = "Unknown status")
    public Page<ForumReportDTO> list(@RequestParam(required = false) ForumReportStatus status,
                                     @ParameterObject Pageable pageable) {
        return reports.list(status, pageable).map(ForumReportDTO::of);
    }

    @PostMapping("/reports/{id}/handle")
    @Operation(summary = "Close a report as handled",
            description = "Idempotent: a report that is already closed (handled or dismissed) is returned unchanged, "
                    + "first decision and date kept. Does not touch the message.")
    @ApiResponse(responseCode = "200", description = "The report")
    @ApiResponse(responseCode = "404", description = "Unknown report")
    public ForumReportDTO handle(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return reports.close(id, ForumReportStatus.HANDLED, admin.getId());
    }

    @PostMapping("/reports/{id}/dismiss")
    @Operation(summary = "Close a report as dismissed", description = "Same rules as handle.")
    @ApiResponse(responseCode = "200", description = "The report")
    @ApiResponse(responseCode = "404", description = "Unknown report")
    public ForumReportDTO dismiss(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return reports.close(id, ForumReportStatus.DISMISSED, admin.getId());
    }

    // ---- threads -------------------------------------------------------------------------------

    @PostMapping("/threads/{id}/hide")
    @Operation(summary = "Hide a thread (and so its replies) from everyone but administrators", description = "Idempotent.")
    @ApiResponse(responseCode = "200", description = "The thread, hidden")
    @ApiResponse(responseCode = "404", description = "Unknown thread")
    public ForumThreadDTO hideThread(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return thread(moderation.hideThread(id, true), admin);
    }

    @PostMapping("/threads/{id}/unhide")
    @Operation(summary = "Show a hidden thread again", description = "Idempotent.")
    @ApiResponse(responseCode = "200", description = "The thread, visible")
    @ApiResponse(responseCode = "404", description = "Unknown thread")
    public ForumThreadDTO unhideThread(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return thread(moderation.hideThread(id, false), admin);
    }

    @PostMapping("/threads/{id}/lock")
    @Operation(summary = "Lock a thread: it stays readable but refuses new replies (409)",
            description = "Idempotent. Takes the thread's row lock, so a reply being created at the same moment either "
                    + "lands before the lock or is refused.")
    @ApiResponse(responseCode = "200", description = "The thread, locked")
    @ApiResponse(responseCode = "404", description = "Unknown thread")
    public ForumThreadDTO lockThread(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return thread(moderation.lockThread(id, true), admin);
    }

    @PostMapping("/threads/{id}/unlock")
    @Operation(summary = "Unlock a thread", description = "Idempotent.")
    @ApiResponse(responseCode = "200", description = "The thread, unlocked")
    @ApiResponse(responseCode = "404", description = "Unknown thread")
    public ForumThreadDTO unlockThread(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return thread(moderation.lockThread(id, false), admin);
    }

    @DeleteMapping("/threads/{id}")
    @Operation(summary = "Delete a thread for good, with its replies and every report on them")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Unknown thread")
    public ResponseEntity<Void> deleteThread(@PathVariable int id) {
        moderation.deleteThread(id);
        return ResponseEntity.noContent().build();
    }

    // ---- replies -------------------------------------------------------------------------------

    @PostMapping("/replies/{id}/hide")
    @Operation(summary = "Hide a reply from everyone but administrators", description = "Idempotent.")
    @ApiResponse(responseCode = "200", description = "The reply, hidden")
    @ApiResponse(responseCode = "404", description = "Unknown reply")
    public ForumReplyDTO hideReply(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return ForumReplyDTO.of(moderation.hideReply(id, true), admin.getId());
    }

    @PostMapping("/replies/{id}/unhide")
    @Operation(summary = "Show a hidden reply again", description = "Idempotent.")
    @ApiResponse(responseCode = "200", description = "The reply, visible")
    @ApiResponse(responseCode = "404", description = "Unknown reply")
    public ForumReplyDTO unhideReply(@PathVariable int id, @AuthenticationPrincipal UserPrincipal admin) {
        return ForumReplyDTO.of(moderation.hideReply(id, false), admin.getId());
    }

    @DeleteMapping("/replies/{id}")
    @Operation(summary = "Delete a reply for good, with every report on it")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Unknown reply")
    public ResponseEntity<Void> deleteReply(@PathVariable int id) {
        moderation.deleteReply(id);
        return ResponseEntity.noContent().build();
    }

    private ForumThreadDTO thread(ForumThread t, UserPrincipal admin) {
        return ForumThreadDTO.of(t, forum.replyCounts(List.of(t.getId()), true).getOrDefault(t.getId(), 0L), admin.getId());
    }
}
