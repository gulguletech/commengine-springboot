package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Log of everything an official gave away (wares and badges): the "Dedicated Items" list. */
@Entity
@Table(
        name = "official_grants",
        indexes = {
                @Index(name = "idx_official_grants_user", columnList = "user_id"),
                @Index(name = "idx_official_grants_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OfficialGrant {

    public static final String WARE = "WARE";
    public static final String BADGE = "BADGE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** WARE or BADGE */
    @Column(nullable = false, length = 8)
    private String kind;

    /** Receiver. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** StoreItem id (WARE) or Badge id (BADGE). */
    @Column(name = "target_id", nullable = false)
    private String targetId;

    /** Snapshot, so the list still reads well if the item is renamed or removed. */
    @Column(name = "target_name", nullable = false, length = 80)
    private String targetName;

    /** FRAME / BUBBLE / ENTRY_EFFECT / BACKGROUND for wares, "BADGE" for badges. */
    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "image_url", length = 600)
    private String imageUrl;

    @Column(name = "given_by", nullable = false)
    private String givenBy;

    /** null = permanent */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
