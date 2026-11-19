package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One-way follow: `followerId` follows `followingId`. No approval needed. */
@Entity
@Table(name = "user_follows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}),
        indexes = {
                @Index(name = "idx_follows_follower", columnList = "follower_id, created_at"),
                @Index(name = "idx_follows_following", columnList = "following_id, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class UserFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "follower_id", nullable = false)
    private String followerId;

    @Column(name = "following_id", nullable = false)
    private String followingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
