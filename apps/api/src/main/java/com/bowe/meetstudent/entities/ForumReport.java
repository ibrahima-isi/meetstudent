package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.enums.ForumReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A user's report of one forum message (V34): exactly one of {@code thread} (the opening message of a thread) or
 * {@code reply} is set. One report per reporter and message. The report goes with its message (cascade); it survives
 * the reporter's account, which only blanks {@code reporter}. Personal data: only administrators read it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Check(constraints = "(thread_id is null) <> (reply_id is null)")
@Table(name = "forum_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_forum_reports_reporter_thread", columnNames = {"reporter_id", "thread_id"}),
                @UniqueConstraint(name = "uq_forum_reports_reporter_reply", columnNames = {"reporter_id", "reply_id"})},
        indexes = {
                @Index(name = "idx_forum_reports_status_created", columnList = "status, created_at DESC, id DESC"),
                @Index(name = "idx_forum_reports_thread", columnList = "thread_id"),
                @Index(name = "idx_forum_reports_reply", columnList = "reply_id")})
public class ForumReport {

    public static final int MAX_REASON = 255;
    public static final int MAX_SNAPSHOT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ForumThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ForumReply reply;

    /** Null once the reporter deleted his account: the report stays, anonymous. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity reporter;

    @Column(nullable = false, length = MAX_REASON)
    private String reason;

    /** The start of the reported text when the report was made (up to {@link #MAX_SNAPSHOT} UTF-16 units). */
    @Column(name = "target_snapshot", nullable = false, length = MAX_SNAPSHOT)
    private String targetSnapshot;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ForumReportStatus status = ForumReportStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null while the report is open; set once, by the first decision. */
    @Column(name = "handled_at")
    private Instant handledAt;

    /** The administrator who closed the report; null while open or once that account is deleted. Never exposed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity handledBy;
}
