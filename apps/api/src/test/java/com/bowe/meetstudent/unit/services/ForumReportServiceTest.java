package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.ForumReply;
import com.bowe.meetstudent.entities.ForumThread;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.ForumReplyRepository;
import com.bowe.meetstudent.repositories.ForumReportRepository;
import com.bowe.meetstudent.repositories.ForumThreadRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.ForumRateLimiter;
import com.bowe.meetstudent.services.ForumReportService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A message deleted between the read and the insert is a clean 404, a concurrent duplicate a clean 409. */
class ForumReportServiceTest {

    private final ForumThreadRepository threads = mock(ForumThreadRepository.class);
    private final ForumReplyRepository replies = mock(ForumReplyRepository.class);
    private final ForumReportRepository reports = mock(ForumReportRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ForumRateLimiter limiter = mock(ForumRateLimiter.class);
    private final ForumReportService service = new ForumReportService(threads, replies, reports, users, limiter, Clock.systemUTC());

    @BeforeEach
    void data() {
        var author = UserEntity.builder().id(1).firstname("Awa").build();
        var thread = ForumThread.builder().id(10).title("t").body("b").author(author).build();
        var reply = ForumReply.builder().id(20).thread(thread).body("r").author(author).build();
        when(threads.findWithAuthorById(10)).thenReturn(Optional.of(thread));
        when(replies.findWithAuthorById(20)).thenReturn(Optional.of(reply));
        when(users.findById(2)).thenReturn(Optional.of(UserEntity.builder().id(2).firstname("Fatou").build()));
    }

    private static DataIntegrityViolationException violation(String sqlState) {
        return new DataIntegrityViolationException("x", new ConstraintViolationException("x", new SQLException("x", sqlState), "c"));
    }

    @Test
    void aForeignKeyViolationOnInsertMeansTheMessageIsGone() {
        when(reports.saveAndFlush(any())).thenThrow(violation("23503"));
        assertThatThrownBy(() -> service.reportThread(10, 2, "spam")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.reportReply(20, 2, "spam")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aUniqueViolationOnInsertMeansAnotherRequestReportedFirst() {
        when(reports.saveAndFlush(any())).thenThrow(violation("23505"));
        assertThatThrownBy(() -> service.reportThread(10, 2, "spam")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.reportReply(20, 2, "spam")).isInstanceOf(ConflictException.class);
    }

    @Test
    void anyOtherIntegrityErrorIsLeftToTheGlobalHandler() {
        when(reports.saveAndFlush(any())).thenThrow(violation("22001"));
        assertThatThrownBy(() -> service.reportThread(10, 2, "spam")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSnapshotIsCutAtAThousandUnitsWithoutSplittingASurrogatePair() {
        org.assertj.core.api.Assertions.assertThat(ForumReportService.snapshot("short")).isEqualTo("short");
        org.assertj.core.api.Assertions.assertThat(ForumReportService.snapshot("x".repeat(1000))).hasSize(1000);
        var cutInsidePair = "x".repeat(999) + "\uD83D\uDE00" + "tail";
        org.assertj.core.api.Assertions.assertThat(ForumReportService.snapshot(cutInsidePair)).isEqualTo("x".repeat(999));
        org.assertj.core.api.Assertions.assertThat(ForumReportService.snapshot("x".repeat(2000))).hasSize(1000);
    }
}
