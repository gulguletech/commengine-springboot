package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Someone the host trusted with host powers in one audio room (can run PK). */
@Entity
@Table(
        name = "audio_room_admins",
        uniqueConstraints = @UniqueConstraint(name = "uq_room_admin", columnNames = {"room_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class AudioRoomAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
