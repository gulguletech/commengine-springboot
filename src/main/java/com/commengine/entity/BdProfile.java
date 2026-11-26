package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A Business Developer: registers agencies under a super admin's team. Becomes one by accepting an invitation. */
@Entity
@Table(name = "bd_profiles")
@Getter
@Setter
@NoArgsConstructor
public class BdProfile {

    @Id
    @Column(name = "user_id")
    private String userId;

    /** The super admin this BD's team works for (target responsibility). */
    @Column(name = "super_admin_id")
    private String superAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
