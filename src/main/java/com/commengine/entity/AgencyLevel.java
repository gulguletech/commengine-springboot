package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Salary ladder edited from the admin panel. A host whose diamonds reach `diamonds` is at this level and earns
 * `hostSalary` (USD); the agency earns `agencySalary` (USD) on top for that host.
 */
@Entity
@Table(
        name = "agency_levels",
        uniqueConstraints = @UniqueConstraint(name = "uq_agency_level_number", columnNames = "level_no")
)
@Getter
@Setter
@NoArgsConstructor
public class AgencyLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "level_no", nullable = false)
    private int level;

    /** Diamonds a host must reach for this level. */
    @Column(nullable = false)
    private long diamonds;

    @Column(name = "host_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal hostSalary = BigDecimal.ZERO;

    @Column(name = "agency_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal agencySalary = BigDecimal.ZERO;
}
