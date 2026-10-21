package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.ForumReply;
import com.bowe.meetstudent.entities.ForumThread;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.ForumReplyRepository;
import com.bowe.meetstudent.repositories.ForumThreadRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator actions on messages: hide/unhide, lock/unlock a thread, delete for good. Hide/unhide and lock/unlock are
 * idempotent (the second call changes nothing and answers 200), a second delete answers 404; all work on hidden
 * messages too. Every writer loads the row with SELECT ... FOR UPDATE (hide, lock and the author's
 * edits alike), so no concurrent write is lost. The text of a message is never edited here. Locking takes the thread's row lock
 * (see {@link ForumThreadRepository#findByIdForUpdate}), the same one {@link ForumService#createReply} waits on.
 */
@Service
@RequiredArgsConstructor
public class ForumModerationService {

    private final ForumThreadRepository threads;
    private final ForumReplyRepository replies;

    @Transactional
    public ForumThread hideThread(int id, boolean hidden) {
        var thread = threads.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        thread.setHidden(hidden);
        return withAuthor(thread);
    }

    /** Takes the row lock: a reply being created waits for this transaction, then sees the new state. */
    @Transactional
    public ForumThread lockThread(int id, boolean locked) {
        var thread = threads.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Thread not found"));
        thread.setLocked(locked);
        return withAuthor(thread);
    }

    @Transactional
    public ForumReply hideReply(int id, boolean hidden) {
        var reply = replies.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Reply not found"));
        reply.setHidden(hidden);
        Hibernate.initialize(reply.getAuthor());
        if (reply.getAuthor() != null) Hibernate.initialize(reply.getAuthor().getRole());
        return reply;
    }

    /** The replies and every report on the thread or on its replies go with it (database cascade). */
    @Transactional
    public void deleteThread(int id) {
        threads.delete(threads.findById(id).orElseThrow(() -> new ResourceNotFoundException("Thread not found")));
    }

    /** The reports on the reply go with it (database cascade). */
    @Transactional
    public void deleteReply(int id) {
        replies.delete(replies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reply not found")));
    }

    /** Loads what the DTO shows while the transaction is open, so the controller does not depend on open-in-view. */
    private static ForumThread withAuthor(ForumThread thread) {
        Hibernate.initialize(thread.getAuthor());
        if (thread.getAuthor() != null) Hibernate.initialize(thread.getAuthor().getRole());
        Hibernate.initialize(thread.getSchool());
        return thread;
    }
}
