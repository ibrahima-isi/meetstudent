package com.bowe.meetstudent.services;

import com.bowe.meetstudent.dto.validation.SafeText;
import com.bowe.meetstudent.entities.ForumReply;
import com.bowe.meetstudent.entities.ForumThread;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.FieldValidationException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.ForumReplyRepository;
import com.bowe.meetstudent.repositories.ForumThreadRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Forum business rules: public reading (hidden messages only for administrators), writing for the callers the
 * controller already vetted, author-only edits and deletes, locked threads, plain text and the per-user write limit
 * (creations and edits share it, deletes are free).
 * A message is stored exactly as received (after the surrounding whitespace is removed): never interpreted.
 */
@Service
@RequiredArgsConstructor
public class ForumService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ForumThreadRepository threads;
    private final ForumReplyRepository replies;
    private final SchoolRepository schools;
    private final UserRepository users;
    private final ForumRateLimiter rateLimiter;
    private final Clock clock;

    // ---- reading -------------------------------------------------------------------------------

    /** Fixed order (newest first); any requested sort is ignored and the size is capped at 100. */
    @Transactional(readOnly = true)
    public Page<ForumThread> listThreads(Integer schoolId, boolean includeHidden, Pageable requested) {
        var page = fixed(requested);
        if (schoolId == null) {
            return includeHidden ? threads.findBy(page) : threads.findByHiddenFalse(page);
        }
        return includeHidden ? threads.findBySchool_Id(schoolId, page) : threads.findBySchool_IdAndHiddenFalse(schoolId, page);
    }

    /** Number of replies per thread id; hidden replies only count for administrators. */
    @Transactional(readOnly = true)
    public Map<Integer, Long> replyCounts(Collection<Integer> threadIds, boolean includeHidden) {
        Map<Integer, Long> counts = new HashMap<>();
        if (threadIds.isEmpty()) return counts;
        for (var row : replies.countByThreadIds(threadIds, includeHidden)) {
            counts.put((Integer) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** @throws ResourceNotFoundException unknown thread, or hidden for a non-admin (404) */
    @Transactional(readOnly = true)
    public ForumThread getThread(int id, boolean includeHidden) {
        return threads.findWithAuthorById(id)
                .filter(t -> includeHidden || !t.isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
    }

    @Transactional(readOnly = true)
    public Page<ForumReply> listReplies(int threadId, boolean includeHidden, Pageable requested) {
        getThread(threadId, includeHidden);
        var page = fixed(requested);
        return includeHidden ? replies.findByThread_Id(threadId, page) : replies.findByThread_IdAndHiddenFalse(threadId, page);
    }

    // ---- threads -------------------------------------------------------------------------------

    /** @throws FieldValidationException forbidden characters, or unknown school (400) */
    @Transactional
    public ForumThread createThread(int authorId, String title, String body, Integer schoolId) {
        checkText("title", title);
        checkText("body", body);
        var school = schoolId == null ? null : schools.findById(schoolId)
                .orElseThrow(() -> new FieldValidationException("schoolId", "Établissement inconnu"));
        var author = author(authorId);
        rateLimiter.check(authorId);
        return threads.save(ForumThread.builder().title(title).body(body).author(author).school(school)
                .createdAt(clock.instant()).build());
    }

    @Transactional
    public ForumThread updateThread(int id, int actorId, String title, String body) {
        var thread = ownThread(id, actorId);
        checkText("title", title);
        checkText("body", body);
        rateLimiter.check(actorId);
        thread.setTitle(title);
        thread.setBody(body);
        thread.setModifiedAt(clock.instant());
        return thread;
    }

    /** The replies go with the thread. */
    @Transactional
    public void deleteThread(int id, int actorId) {
        threads.delete(ownThread(id, actorId));
    }

    // ---- replies -------------------------------------------------------------------------------

    /**
     * The thread is read under its row lock (SELECT ... FOR UPDATE), like {@link ForumModerationService#lockThread}: a
     * reply and a lock on the same thread are serialised, so no reply is saved after a lock committed. Replies to
     * one thread queue on that row for the length of this short transaction.
     *
     * @throws ConflictException the thread is locked (409)
     */
    @Transactional
    public ForumReply createReply(int threadId, int authorId, String body) {
        var thread = threads.findByIdForUpdate(threadId).filter(t -> !t.isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        if (thread.isLocked()) {
            throw new ConflictException("This thread is locked");
        }
        checkText("body", body);
        var author = author(authorId);
        rateLimiter.check(authorId);
        return replies.save(ForumReply.builder().thread(thread).author(author).body(body).createdAt(clock.instant()).build());
    }

    @Transactional
    public ForumReply updateReply(int id, int actorId, String body) {
        var reply = ownReply(id, actorId);
        checkText("body", body);
        rateLimiter.check(actorId);
        reply.setBody(body);
        reply.setModifiedAt(clock.instant());
        return reply;
    }

    @Transactional
    public void deleteReply(int id, int actorId) {
        replies.delete(ownReply(id, actorId));
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** The thread under its row lock: an edit or delete and a hide/lock on the same row are serialised. */
    private ForumThread ownThread(int id, int actorId) {
        var thread = threads.findByIdForUpdate(id).filter(t -> !t.isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        requireAuthor(thread.getAuthor(), actorId);
        return thread;
    }

    private ForumReply ownReply(int id, int actorId) {
        var reply = replies.findByIdForUpdate(id).filter(r -> !r.isHidden() && !r.getThread().isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Reply not found"));
        requireAuthor(reply.getAuthor(), actorId);
        return reply;
    }

    private static void requireAuthor(UserEntity author, int actorId) {
        if (author == null || !author.getId().equals(actorId)) {
            throw new AccessDeniedException("Only the author can change this message");
        }
    }

    private UserEntity author(int id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static void checkText(String field, String text) {
        if (SafeText.hasForbiddenCharacters(text)) {
            throw new FieldValidationException(field, "Caractères non autorisés (contrôle ou invisibles)");
        }
    }

    private static Pageable fixed(Pageable requested) {
        return PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE), NEWEST_FIRST);
    }
}
