package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Append-only ledger. (userId, type, ref) is unique so the same game round can never pay twice. */
@Entity
@Table(
        name = "wallet_transactions",
        indexes = @Index(name = "idx_wallet_tx_user_created", columnList = "user_id, created_at"),
        uniqueConstraints = @UniqueConstraint(name = "uq_wallet_tx_user_type_ref", columnNames = {"user_id", "type", "ref"})
)
@Getter
@Setter
@NoArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** COINS or BEANS */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** Signed: positive = credit, negative = debit. */
    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    /** e.g. WELCOME_BONUS, ADMIN_CREDIT, GAME_BET, GAME_WIN */
    @Column(name = "type", nullable = false, length = 40)
    private String type;

    /** Idempotency key, e.g. "lucky-draw:1234". Optional. */
    @Column(name = "ref", length = 120)
    private String ref;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
