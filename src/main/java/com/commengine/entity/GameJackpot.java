package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The running jackpot pool of a game (one row per game). Created automatically (ddl-auto: update). */
@Entity
@Table(name = "game_jackpots")
@Getter
@Setter
@NoArgsConstructor
public class GameJackpot {

    @Id
    @Column(name = "game", length = 40)
    private String game;

    @Column(name = "pool", nullable = false)
    private long pool;

    /** last round whose jackpot has been paid (guards against paying a round twice) */
    @Column(name = "last_paid_round", nullable = false)
    private long lastPaidRound = -1;

    public GameJackpot(String game, long pool) {
        this.game = game;
        this.pool = pool;
    }
}
