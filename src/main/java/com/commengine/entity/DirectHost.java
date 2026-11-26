package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A host who works directly under a super admin, without an agency ("Direct / Solo"). */
@Entity
@Table(name = "direct_hosts")
@Getter
@Setter
@NoArgsConstructor
public class DirectHost {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "super_admin_id", nullable = false)
    private String superAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
