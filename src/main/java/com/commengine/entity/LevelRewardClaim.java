package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row per (user, level) whose reward has been collected. The unique key makes double-collecting impossible. */
@Entity
@Table(
        name = "level_reward_claims",
        uniqueConstraints = @UniqueConstraint(name = "uq_level_claim_user_level", columnNames = {"user_id", "level_number"})
)
@Getter
@Setter
@NoArgsConstructor
public class LevelRewardClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "level_number", nullable = false)
    private int level;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt = Instant.now();
}
