package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Numbers the admin panel sets for each super admin: salary, commission and targets. Display only. */
@Entity
@Table(name = "super_admin_settings")
@Getter
@Setter
@NoArgsConstructor
public class SuperAdminSettings {

    @Id
    @Column(name = "user_id")
    private String userId;

    /** USD shown as "Monthly Salary". */
    @Column(name = "monthly_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlySalary = BigDecimal.ZERO;

    /** Percent, e.g. 3.00. Income shown = team gift coins x rate / 100. */
    @Column(name = "commission_rate", nullable = false, precision = 6, scale = 2)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    /** The big "Target" (coins) on the dashboard. */
    @Column(name = "target_coins", nullable = false)
    private long targetCoins = 0;

    /** Coins every agency should reach in a month (the "Target per agency"). */
    @Column(name = "agency_target_coins", nullable = false)
    private long agencyTargetCoins = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
