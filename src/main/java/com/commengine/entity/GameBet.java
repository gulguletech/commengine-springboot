package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row per bet a player places in any round-based game. */
@Entity
@Table(
        name = "game_bets",
        indexes = {
                @Index(name = "idx_game_bets_round", columnList = "game, round"),
                @Index(name = "idx_game_bets_unsettled", columnList = "game, settled, round"),
                @Index(name = "idx_game_bets_user_round", columnList = "user_id, game, round")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class GameBet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "game", nullable = false, length = 40)
    private String game;

    @Column(name = "round", nullable = false)
    private long round;

    @Column(name = "option_index", nullable = false)
    private int optionIndex;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "settled", nullable = false)
    private boolean settled = false;

    @Column(name = "payout", nullable = false)
    private long payout = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
