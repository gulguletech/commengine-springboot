package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row per Cashfree checkout attempt. Coins are credited exactly once, when status flips to PAID. */
@Entity
@Table(
        name = "payment_orders",
        indexes = @Index(name = "idx_payment_orders_user_created", columnList = "user_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
public class PaymentOrder {

    /** Our order id, sent to Cashfree as order_id (e.g. CE_ab12...). */
    @Id
    @Column(name = "order_id", length = 50)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "package_id", nullable = false, length = 40)
    private String packageId;

    @Column(name = "coins", nullable = false)
    private long coins;

    /** Price in INR rupees, fixed server-side from the package catalog. */
    @Column(name = "amount_inr", nullable = false)
    private long amountInr;

    /** CREATED -> PAID | FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "CREATED";

    @Column(name = "cf_order_id", length = 60)
    private String cfOrderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;
}
