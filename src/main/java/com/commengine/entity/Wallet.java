package com.commengine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per user. Coins = play currency (games). Beans = earnings currency. */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "coins", nullable = false)
    private long coins;

    @Column(name = "beans", nullable = false)
    private long beans;
}
