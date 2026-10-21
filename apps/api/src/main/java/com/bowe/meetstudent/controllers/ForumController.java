package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.ForumReplyDTO;
import com.bowe.meetstudent.dto.ForumReplyRequest;
import com.bowe.meetstudent.dto.ForumThreadDTO;
import com.bowe.meetstudent.dto.ForumThreadRequest;
import com.bowe.meetstudent.exceptions.ErrorResponse;
import com.bowe.meetstudent.security.RequiresForumWriter;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.ForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Forum: public reading, writing for students and approved experts. Plain text only. Hidden messages are only
 * ever returned to administrators.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/forum")
@Tag(name = "16. Forum", description = "Threads and replies: public reading, writing for students and approved experts")
public class ForumController {

    private final ForumService service;

    // ---- threads -------------------------------------------------------------------------------

    @GetMapping("/threads")
    @Operation(summary = "List threads (paginated, public)",
            description = "Newest first; the order is fixed (?sort= is ignored) and the page size is capped at 100. "
                    + "Optional ?schoolId= keeps the threads about one school (an unknown school gives an empty page). "
                    + "Hidden threads are returned to administrators only.")
    @ApiResponse(responseCode = "200", description = "Page of threads")
    public Page<ForumThreadDTO> listThreads(@RequestParam(required = false) Integer schoolId,
                                            @ParameterObject Pageable pageable,
                                            Authentication authentication,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        var includeHidden = isAdmin(authentication);
        var page = service.listThreads(schoolId, includeHidden, pageable);
        var counts = service.replyCounts(page.getContent().stream().map(t -> t.getId()).toList(), includeHidden);
        var viewer = viewerId(principal);
        return page.map(t -> ForumThreadDTO.of(t, counts.getOrDefault(t.getId(), 0L), viewer));
    }

    @GetMapping("/threads/{id}")
    @Operation(summary = "Read a thread (public)")
    @ApiResponse(responseCode = "200", description = "The thread")
    @ApiResponse(responseCode = "404", description = "Unknown thread, or hidden for a non-administrator")
    public ForumThreadDTO getThread(@PathVariable int id, Authentication authentication,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        var includeHidden = isAdmin(authentication);
        var thread = service.getThread(id, includeHidden);
        return ForumThreadDTO.of(thread, service.replyCounts(List.of(id), includeHidden).getOrDefault(id, 0L),
                viewerId(principal));
    }

    @PostMapping("/threads")
    @RequiresForumWriter
    @Operation(summary = "Open a thread",
            description = "Students and approved experts. Title up to 150 and message up to 5000 characters, plain text "
                    + "(control and invisible characters refused). Optional schoolId. At most 5 messages per minute "
                    + "and per user, threads and replies together.")
    @ApiResponse(responseCode = "201", description = "Thread created")
    @ApiResponse(responseCode = "400", description = "Validation error: {field: message}; unknown school: {\"schoolId\": ...}")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "403", description = "Not a student or an approved expert")
    @ApiResponse(responseCode = "429", description = "Too many messages; see Retry-After (seconds)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ForumThreadDTO> createThread(@RequestBody @Valid ForumThreadRequest request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        var thread = service.createThread(principal.getId(), request.title(), request.body(), request.schoolId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ForumThreadDTO.of(thread, 0, principal.getId()));
    }

    @PutMapping("/threads/{id}")
    @RequiresForumWriter
    @Operation(summary = "Edit a thread (author only)",
            description = "Replaces title and message; schoolId in the body is ignored (a thread keeps its school). "
                    + "Allowed on a locked thread. Counts toward the write limit (5 per minute and per user, with creations); deletes do not.")
    @ApiResponse(responseCode = "200", description = "The updated thread")
    @ApiResponse(responseCode = "403", description = "Not the author")
    @ApiResponse(responseCode = "404", description = "Unknown or hidden thread")
    @ApiResponse(responseCode = "429", description = "Too many writes; see Retry-After (seconds)")
    public ForumThreadDTO updateThread(@PathVariable int id, @RequestBody @Valid ForumThreadRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        var thread = service.updateThread(id, principal.getId(), request.title(), request.body());
        return ForumThreadDTO.of(thread, service.replyCounts(List.of(id), false).getOrDefault(id, 0L), principal.getId());
    }

    @DeleteMapping("/threads/{id}")
    @RequiresForumWriter
    @Operation(summary = "Delete a thread and its replies (author only)")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "Not the author")
    @ApiResponse(responseCode = "404", description = "Unknown or hidden thread")
    public ResponseEntity<Void> deleteThread(@PathVariable int id, @AuthenticationPrincipal UserPrincipal principal) {
        service.deleteThread(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ---- replies -------------------------------------------------------------------------------

    @GetMapping("/threads/{id}/replies")
    @Operation(summary = "List the replies of a thread (paginated, public)",
            description = "Newest first, fixed order, page size capped at 100. Hidden replies are returned to administrators only.")
    @ApiResponse(responseCode = "200", description = "Page of replies")
    @ApiResponse(responseCode = "404", description = "Unknown thread, or hidden for a non-administrator")
    public Page<ForumReplyDTO> listReplies(@PathVariable int id, @ParameterObject Pageable pageable,
                                           Authentication authentication, @AuthenticationPrincipal UserPrincipal principal) {
        var viewer = viewerId(principal);
        return service.listReplies(id, isAdmin(authentication), pageable).map(r -> ForumReplyDTO.of(r, viewer));
    }

    @PostMapping("/threads/{id}/replies")
    @RequiresForumWriter
    @Operation(summary = "Reply to a thread",
            description = "Students and approved experts. Body up to 5000 characters, plain text. Same rate limit as thread creation.")
    @ApiResponse(responseCode = "201", description = "Reply created")
    @ApiResponse(responseCode = "400", description = "Validation error: {field: message}")
    @ApiResponse(responseCode = "403", description = "Not a student or an approved expert")
    @ApiResponse(responseCode = "404", description = "Unknown or hidden thread")
    @ApiResponse(responseCode = "409", description = "The thread is locked")
    @ApiResponse(responseCode = "429", description = "Too many messages; see Retry-After (seconds)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ForumReplyDTO> createReply(@PathVariable int id, @RequestBody @Valid ForumReplyRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        var reply = service.createReply(id, principal.getId(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ForumReplyDTO.of(reply, principal.getId()));
    }

    @PutMapping("/replies/{id}")
    @RequiresForumWriter
    @Operation(summary = "Edit a reply (author only)", description = "Allowed on a locked thread. Counts toward the write limit (5 per minute and per user, with creations); deletes do not.")
    @ApiResponse(responseCode = "200", description = "The updated reply")
    @ApiResponse(responseCode = "403", description = "Not the author")
    @ApiResponse(responseCode = "404", description = "Unknown or hidden reply")
    @ApiResponse(responseCode = "429", description = "Too many writes; see Retry-After (seconds)")
    public ForumReplyDTO updateReply(@PathVariable int id, @RequestBody @Valid ForumReplyRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ForumReplyDTO.of(service.updateReply(id, principal.getId(), request.body()), principal.getId());
    }

    @DeleteMapping("/replies/{id}")
    @RequiresForumWriter
    @Operation(summary = "Delete a reply (author only)")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "Not the author")
    @ApiResponse(responseCode = "404", description = "Unknown or hidden reply")
    public ResponseEntity<Void> deleteReply(@PathVariable int id, @AuthenticationPrincipal UserPrincipal principal) {
        service.deleteReply(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static Integer viewerId(UserPrincipal principal) {
        return principal == null ? null : principal.getId();
    }
}
