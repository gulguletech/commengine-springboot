package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A PAID store item a user owns until expiresAt (bought, or received as a gift). Free items need no row. */
@Entity
@Table(
        name = "user_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_items_user_item", columnNames = {"user_id", "item_id"}),
        indexes = @Index(name = "idx_user_items_user", columnList = "user_id, expires_at")
)
@Getter
@Setter
@NoArgsConstructor
public class UserItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean isActive() {
        return expiresAt.isAfter(Instant.now());
    }
}
