package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An invitation to join the super admin chain, shown in the person's inbox until they accept or decline.
 *   BD     : super admin -> user becomes a BD under that super admin
 *   AGENCY : super admin or BD -> user becomes the owner of a NEW agency (details below)
 *   HOST   : super admin -> user becomes a direct host
 * (Agency owner -> host invitations still use AgencyInvite.)
 */
@Entity
@Table(
        name = "team_invites",
        indexes = {
                @Index(name = "idx_team_invite_to", columnList = "to_user_id, status"),
                @Index(name = "idx_team_invite_from", columnList = "from_user_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TeamInvite {

    public static final String BD = "BD";
    public static final String AGENCY = "AGENCY";
    public static final String HOST = "HOST";

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 8)
    private String kind;

    @Column(name = "from_user_id", nullable = false)
    private String fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private String toUserId;

    /** The super admin the invited person ends up under. */
    @Column(name = "super_admin_id")
    private String superAdminId;

    /** Set when a BD sent an AGENCY invite: the agency is registered under this BD. */
    @Column(name = "bd_user_id")
    private String bdUserId;

    // ---- AGENCY invites only
    @Column(name = "agency_name", length = 80)
    private String agencyName;

    @Column(name = "whatsapp", length = 30)
    private String whatsapp;

    @Column(name = "country", length = 60)
    private String country;

    @Column(name = "logo_url", length = 600)
    private String logoUrl;

    @Column(name = "id_proof_url", length = 600)
    private String idProofUrl;

    /** Internal user ids (comma separated, up to 5) who get a host invite once the agency exists. */
    @Column(name = "host_ids", length = 400)
    private String hostIds;

    @Column(nullable = false, length = 12)
    private String status = PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;
}
