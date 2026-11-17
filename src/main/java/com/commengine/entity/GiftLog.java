package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One gift send, kept for the rankings. `coins` is the full coin value (price x count).
 * Star list = who received the most; Contribution list = who sent the most, over today / this week / this month.
 */
@Entity
@Table(
        name = "gift_logs",
        indexes = {
                @Index(name = "idx_gift_logs_created", columnList = "created_at"),
                @Index(name = "idx_gift_logs_receiver", columnList = "receiver_id, created_at"),
                @Index(name = "idx_gift_logs_sender", columnList = "sender_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class GiftLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(name = "receiver_id", nullable = false)
    private String receiverId;

    @Column(name = "coins", nullable = false)
    private long coins;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
