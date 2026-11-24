package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A PK (battle) in an audio room. Players' scores live in PkParticipant. */
@Entity
@Table(
        name = "pk_battles",
        indexes = {
                @Index(name = "idx_pk_room_status", columnList = "room_id, status"),
                @Index(name = "idx_pk_status_ends", columnList = "status, ends_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PkBattle {

    public static final String RUNNING = "RUNNING";
    public static final String ENDED = "ENDED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    /** 1V1 or MULTI */
    @Column(nullable = false, length = 8)
    private String mode;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 10)
    private String status = RUNNING;

    @Column(name = "started_by", nullable = false)
    private String startedBy;

    @Column(name = "ended_at")
    private Instant endedAt;
}
