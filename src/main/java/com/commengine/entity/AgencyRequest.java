package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A user's application to run an agency. The main admin or any super admin approves it, which creates the agency. */
@Entity
@Table(
        name = "agency_requests",
        indexes = {
                @Index(name = "idx_agency_request_status", columnList = "status, created_at"),
                @Index(name = "idx_agency_request_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AgencyRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** The applicant: becomes the agency owner when approved. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "full_name", nullable = false, length = 80)
    private String fullName;

    @Column(name = "agency_name", nullable = false, length = 80)
    private String agencyName;

    @Column(name = "experience", nullable = false, length = 1000)
    private String experience;

    @Column(name = "expected_hosts", nullable = false)
    private int expectedHosts;

    @Column(name = "aadhaar_front_url", nullable = false, length = 600)
    private String aadhaarFrontUrl;

    @Column(name = "aadhaar_back_url", nullable = false, length = 600)
    private String aadhaarBackUrl;

    @Column(name = "selfie_url", nullable = false, length = 600)
    private String selfieUrl;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** "Admin: name" or the super admin's name. */
    @Column(name = "decided_by", length = 120)
    private String decidedBy;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    /** The agency made when approved. */
    @Column(name = "agency_id")
    private String agencyId;
}
