package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** How much gift value one user has received so far. Their level is worked out from this and the admin's thresholds. */
@Entity
@Table(name = "user_levels")
@Getter
@Setter
@NoArgsConstructor
public class UserLevel {

    @Id
    @Column(name = "user_id")
    private String userId;

    /** Sum of the coin price of every gift this user has received (self-gifts excluded). */
    @Column(name = "received_coins", nullable = false)
    private long receivedCoins = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
