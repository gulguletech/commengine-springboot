package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audio_rooms")
@Getter
@Setter
@NoArgsConstructor
public class AudioRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type = RoomType.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.ACTIVE;

    @Column(nullable = false, unique = true)
    private String channelName;

    private String coverUrl;

    /** One of Catalog.ROOM_CATEGORIES (FUN, MUSIC, GAME, ...). */
    @Column(length = 20)
    private String category;

    /** The host's language when the room was created. */
    @Column(length = 30)
    private String language;

    /** How many seats the room has: 4, 8, 12, 16 or 20. The host picks it when creating the room and can change it later. */
    @Column(name = "seat_count", nullable = false, columnDefinition = "integer default 12")
    private int seatCount = 8;

    /** Every language the host speaks when the room was created ("Hindi,Telugu"). Home's language filter matches on any of them. */
    @Column(name = "languages", length = 300)
    private String languages;

    /** Never serialize this to a client response — see AudioRoomService#sanitize. */
    private String passcode;

    // ---- background from the store (denormalised copy, set by the host) ----
    @Column(name = "background_item_id")
    private String backgroundItemId;
    @Column(name = "background_url", length = 600)
    private String backgroundUrl;
    @Column(name = "background_thumb_url", length = 600)
    private String backgroundThumbUrl;
    private Double backgroundScrim;
    /** When the host's rental of a paid background runs out. null = free background. */
    private Instant backgroundExpiresAt;

    public boolean hasActiveBackground() {
        return backgroundUrl != null && (backgroundExpiresAt == null || backgroundExpiresAt.isAfter(Instant.now()));
    }

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant endedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioRoom other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}