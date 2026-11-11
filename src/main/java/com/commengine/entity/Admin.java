package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An admin-panel account. Completely separate from `users` (phone/OTP accounts):
 * admins sign in with username + password only. See AdminAuthService / AdminBootstrapRunner.
 */
@Entity
@Table(name = "admins")
@Getter
@Setter
@NoArgsConstructor
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Bumped whenever the password changes; old admin tokens carry the old value and stop working. */
    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
