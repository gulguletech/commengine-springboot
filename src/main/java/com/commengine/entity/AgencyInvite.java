package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** An agency owner inviting a user to host for the agency. The user accepts or declines. */
@Entity
@Table(
        name = "agency_invites",
        indexes = {
                @Index(name = "idx_agency_invite_user", columnList = "user_id, status"),
                @Index(name = "idx_agency_invite_agency", columnList = "agency_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AgencyInvite {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agency_id", nullable = false)
    private String agencyId;

    /** The invited user. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, length = 12)
    private String status = PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;
}
