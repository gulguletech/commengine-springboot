package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One player in a battle. `score` = coin value of the gifts they received while it ran. */
@Entity
@Table(
        name = "pk_participants",
        indexes = {
                @Index(name = "idx_pk_part_battle", columnList = "battle_id"),
                @Index(name = "idx_pk_part_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PkParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "battle_id", nullable = false)
    private String battleId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** 0 = first picked (left side in 1v1). */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private long score = 0;
}
