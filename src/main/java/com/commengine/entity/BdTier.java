package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * BD commission ladder (admin panel). When an agency under a BD reaches `minSalary` USD of host salary in a month,
 * the BD earns `commission` USD for it (e.g. $5 at $100, $11 at $200).
 */
@Entity
@Table(name = "bd_tiers", uniqueConstraints = @UniqueConstraint(name = "uq_bd_tier_min", columnNames = "min_salary"))
@Getter
@Setter
@NoArgsConstructor
public class BdTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "min_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal minSalary = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal commission = BigDecimal.ZERO;
}
