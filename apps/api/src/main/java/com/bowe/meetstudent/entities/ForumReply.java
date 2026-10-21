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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** A reply to a thread (flat: replies are not nested). Plain text only (V33). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "forum_replies", indexes = {
        @Index(name = "idx_forum_replies_thread_created", columnList = "thread_id, created_at DESC, id DESC"),
        @Index(name = "idx_forum_replies_author", columnList = "author_id")})
public class ForumReply {

    public static final int MAX_BODY = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ForumThread thread;

    @Column(nullable = false, length = MAX_BODY)
    private String body;

    /** Null once the author deleted his account: the reply stays. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity author;

    /** Set by moderation; a hidden reply is invisible to everyone but administrators. */
    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "modified_at")
    private Instant modifiedAt;
}
