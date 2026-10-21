package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.ForumReportDTO;
import com.bowe.meetstudent.dto.validation.SafeText;
import com.bowe.meetstudent.entities.ForumReport;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ForumReportStatus;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.ForumReplyRepository;
import com.bowe.meetstudent.repositories.ForumReportRepository;
import com.bowe.meetstudent.repositories.ForumThreadRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;

/**
 * Reports on forum messages. Creation: any signed-in non-admin (the controller vets the role), one report per user and
 * message, never on one's own message, never on a hidden or unknown one, with its own rate budget. The queue is read
 * by administrators only and closing a report is idempotent: the first decision and its date stand.
 */
@Service
@RequiredArgsConstructor
public class ForumReportService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ForumThreadRepository threads;
    private final ForumReplyRepository replies;
    private final ForumReportRepository reports;
    private final UserRepository users;
    private final ForumRateLimiter rateLimiter;
    private final Clock clock;

    /**
     * @throws ResourceNotFoundException unknown or hidden thread (404); @throws ConflictException own message or already
     *                                   reported by this user (409); @throws FieldValidationException bad reason (400)
     */
    @Transactional
    public void reportThread(int threadId, int reporterId, String reason) {
        var thread = threads.findWithAuthorById(threadId).filter(t -> !t.isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        checkReason(reason);
        rejectOwnMessage(thread.getAuthor(), reporterId);
        if (reports.existsByReporter_IdAndThread_Id(reporterId, threadId)) {
            throw new ConflictException("You already reported this message");
        }
        var reporter = reporter(reporterId);
        rateLimiter.checkReport(reporterId);
        save(ForumReport.builder().thread(thread).reporter(reporter).reason(reason)
                .targetSnapshot(snapshot(thread.getBody())).createdAt(clock.instant()).build());
    }

    /** Same rules as {@link #reportThread}; a reply of a hidden thread is hidden too (404). */
    @Transactional
    public void reportReply(int replyId, int reporterId, String reason) {
        var reply = replies.findWithAuthorById(replyId).filter(r -> !r.isHidden() && !r.getThread().isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Reply not found"));
        checkReason(reason);
        rejectOwnMessage(reply.getAuthor(), reporterId);
        if (reports.existsByReporter_IdAndReply_Id(reporterId, replyId)) {
            throw new ConflictException("You already reported this message");
        }
        var reporter = reporter(reporterId);
        rateLimiter.checkReport(reporterId);
        save(ForumReport.builder().reply(reply).reporter(reporter).reason(reason)
                .targetSnapshot(snapshot(reply.getBody())).createdAt(clock.instant()).build());
    }

    /** Fixed order (newest first, then id desc): any requested sort is ignored, the size is capped at 100. */
    @Transactional(readOnly = true)
    public Page<ForumReport> list(ForumReportStatus status, Pageable requested) {
        var page = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE), NEWEST_FIRST);
        return status == null ? reports.findBy(page) : reports.findByStatus(status, page);
    }

    /**
     * Idempotent: a report that is already closed is returned unchanged (first decision, date and administrator kept).
     * The DTO is built here, inside the transaction, so the caller needs no open session.
     */
    @Transactional
    public ForumReportDTO close(int id, ForumReportStatus decision, int adminId) {
        var report = reports.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (report.getStatus() == ForumReportStatus.OPEN) {
            report.setStatus(decision);
            report.setHandledAt(clock.instant());
            report.setHandledBy(users.findById(adminId).orElse(null));
        }
        return ForumReportDTO.of(report);
    }

    /**
     * A message deleted between the read and the insert breaks the foreign key (404, as if it had been deleted
     * earlier) and a concurrent duplicate breaks the unique constraint (409): both are expected races, not server errors.
     */
    private void save(ForumReport report) {
        try {
            reports.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            var state = sqlState(e);
            if ("23503".equals(state)) throw new ResourceNotFoundException("Message not found");
            if ("23505".equals(state)) throw new ConflictException("You already reported this message");
            throw e;
        }
    }

    private static String sqlState(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) return sql.getSQLState();
        }
        return null;
    }

    /** The start of the text, at most {@link ForumReport#MAX_SNAPSHOT} UTF-16 units, never cutting a surrogate pair. */
    public static String snapshot(String text) {
        if (text.length() <= ForumReport.MAX_SNAPSHOT) return text;
        var end = ForumReport.MAX_SNAPSHOT;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private static void rejectOwnMessage(UserEntity author, int reporterId) {
        if (author != null && author.getId().equals(reporterId)) {
            throw new ConflictException("You cannot report your own message");
        }
    }

    private UserEntity reporter(int id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static void checkReason(String reason) {
        if (SafeText.hasForbiddenCharacters(reason)) {
            throw new FieldValidationException("reason", "Le motif contient des caractères non autorisés");
        }
    }
}
