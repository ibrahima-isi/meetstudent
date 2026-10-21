package com.bowe.meetstudent.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** A forum thread: a title and an opening message, optionally about one school. Plain text only (V33). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@DynamicUpdate
@Table(name = "forum_threads", indexes = {
        @Index(name = "idx_forum_threads_created_at", columnList = "created_at DESC, id DESC"),
        @Index(name = "idx_forum_threads_school", columnList = "school_id, created_at DESC, id DESC"),
        @Index(name = "idx_forum_threads_author", columnList = "author_id")})
public class ForumThread {

    public static final int MAX_TITLE = 150;
    public static final int MAX_BODY = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(nullable = false, length = MAX_BODY)
    private String body;

    /** Null once the author deleted his account: the thread stays. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity author;

    /** Optional; null when the thread is general or the school was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private School school;

    @Builder.Default
    @Column(nullable = false)
    private boolean locked = false;

    /** Set by moderation; a hidden thread is invisible to everyone but administrators. */
    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null until the author edits the message. */
    @Column(name = "modified_at")
    private Instant modifiedAt;
}
