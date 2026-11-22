package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One stretch of a host being inside their OWN audio room or live video. Only recorded for agency members.
 * Adds up to the "working time" shown in the agency dashboard. endedAt = null means it is still running.
 */
@Entity
@Table(
        name = "host_sessions",
        indexes = {
                @Index(name = "idx_host_session_user", columnList = "user_id, started_at"),
                @Index(name = "idx_host_session_open", columnList = "ended_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class HostSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** AUDIO or VIDEO */
    @Column(nullable = false, length = 8)
    private String kind;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Touched every minute while open, so a crash can be closed at the last moment we knew the host was there. */
    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt = Instant.now();
}
