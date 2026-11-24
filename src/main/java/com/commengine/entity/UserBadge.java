package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A badge a user holds. expiresAt = null means permanent. */
@Entity
@Table(
        name = "user_badges",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_badge", columnNames = {"user_id", "badge_id"}),
        indexes = @Index(name = "idx_user_badges_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "badge_id", nullable = false)
    private String badgeId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isActive() {
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
