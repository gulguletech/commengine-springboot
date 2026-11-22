package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A user belongs to at most ONE agency (unique user_id). The owner is a member too, with role OWNER. */
@Entity
@Table(
        name = "agency_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_agency_member_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_agency_member_agency", columnList = "agency_id")
)
@Getter
@Setter
@NoArgsConstructor
public class AgencyMember {

    public static final String OWNER = "OWNER";
    public static final String HOST = "HOST";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agency_id", nullable = false)
    private String agencyId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** OWNER or HOST */
    @Column(nullable = false, length = 10)
    private String role = HOST;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();
}
