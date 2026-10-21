package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.services.ForumModerationService;
import com.bowe.meetstudent.services.ForumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import static com.bowe.meetstudent.integration.controllers.forum.ForumRaceSupport.get;
import static com.bowe.meetstudent.integration.controllers.forum.ForumRaceSupport.startAndAwaitBlocked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Races on a thread/reply row between the moderation actions (hide, lock) and the other writers (a reply being
 * posted, the author editing). Every writer of {@code hidden}/{@code locked}/text loads the row with
 * SELECT ... FOR UPDATE and Hibernate updates only the changed columns (@DynamicUpdate), so a concurrent transaction
 * waits and then works on the committed state: no lost update, no reply accepted after a lock.
 */
class ForumLockRaceIntegrationTests extends ForumTestSupport {

    @Autowired ForumService forum;
    @Autowired ForumModerationService moderation;
    @Autowired PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource dataSource;

    @Test
    void aReplyStartedWhileTheLockIsBeingWrittenWaitsThenIsRefused() throws Exception {
        var t = thread(student(), "race");
        var replier = student();
        var admin = new ForumRaceSupport.Held(transactions, () -> moderation.lockThread(t.getId(), true)).awaitHeld();

        var reply = startAndAwaitBlocked(dataSource, () -> forum.createReply(t.getId(), replier.getId(), "too late"));
        admin.release();

        assertThatThrownBy(() -> get(reply)).isInstanceOf(ConflictException.class);
        assertThat(replies.count()).isZero();
        assertThat(threads.findById(t.getId()).orElseThrow().isLocked()).isTrue();
    }

    @Test
    void aHideStartedWhileTheAuthorIsEditingWaitsAndBothSurvive() throws Exception {
        var author = student();
        var t = thread(author, "original");
        var edit = new ForumRaceSupport.Held(transactions,
                () -> forum.updateThread(t.getId(), author.getId(), "edited", "edited body")).awaitHeld();

        var hide = startAndAwaitBlocked(dataSource, () -> moderation.hideThread(t.getId(), true));
        edit.release();
        get(hide);

        var after = threads.findById(t.getId()).orElseThrow();
        assertThat(after.isHidden()).as("the hide must not be undone by the edit").isTrue();
        assertThat(after.getTitle()).isEqualTo("edited");
    }

    @Test
    void anEditStartedWhileTheAdminIsHidingWaitsThenFindsTheThreadHidden() throws Exception {
        var author = student();
        var t = thread(author, "original");
        var hiding = new ForumRaceSupport.Held(transactions, () -> moderation.hideThread(t.getId(), true)).awaitHeld();

        var edit = startAndAwaitBlocked(dataSource, () -> forum.updateThread(t.getId(), author.getId(), "edited", "edited body"));
        hiding.release();

        assertThatThrownBy(() -> get(edit)).isInstanceOf(ResourceNotFoundException.class);
        var after = threads.findById(t.getId()).orElseThrow();
        assertThat(after.isHidden()).isTrue();
        assertThat(after.getTitle()).isEqualTo("original");
    }

    @Test
    void aHideAndALockOnTheSameThreadBothStick() throws Exception {
        var t = thread(student(), "both");
        var locking = new ForumRaceSupport.Held(transactions, () -> moderation.lockThread(t.getId(), true)).awaitHeld();

        var hide = startAndAwaitBlocked(dataSource, () -> moderation.hideThread(t.getId(), true));
        locking.release();
        get(hide);

        var after = threads.findById(t.getId()).orElseThrow();
        assertThat(after.isLocked()).isTrue();
        assertThat(after.isHidden()).isTrue();
    }

    @Test
    void aReplyHideStartedWhileTheAuthorIsEditingWaitsAndBothSurvive() throws Exception {
        var author = student();
        var r = reply(thread(student(), "t"), author, "original");
        var edit = new ForumRaceSupport.Held(transactions,
                () -> forum.updateReply(r.getId(), author.getId(), "edited")).awaitHeld();

        var hide = startAndAwaitBlocked(dataSource, () -> moderation.hideReply(r.getId(), true));
        edit.release();
        get(hide);

        var after = replies.findById(r.getId()).orElseThrow();
        assertThat(after.isHidden()).isTrue();
        assertThat(after.getBody()).isEqualTo("edited");
    }

    @Test
    void anEditOfAReplyStartedWhileTheAdminIsHidingItWaitsThenFindsItHidden() throws Exception {
        var author = student();
        var r = reply(thread(student(), "t"), author, "original");
        var hiding = new ForumRaceSupport.Held(transactions, () -> moderation.hideReply(r.getId(), true)).awaitHeld();

        var edit = startAndAwaitBlocked(dataSource, () -> forum.updateReply(r.getId(), author.getId(), "edited"));
        hiding.release();

        assertThatThrownBy(() -> get(edit)).isInstanceOf(ResourceNotFoundException.class);
        var after = replies.findById(r.getId()).orElseThrow();
        assertThat(after.isHidden()).isTrue();
        assertThat(after.getBody()).isEqualTo("original");
    }
}
