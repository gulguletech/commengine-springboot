package com.commengine.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public final class SuperAdminRequests {

    private SuperAdminRequests() {}

    /** Admin panel: make a user a super admin (with their numbers). */
    @Getter
    @Setter
    public static class Create {
        @NotBlank private String userId;
        @DecimalMin("0") private BigDecimal monthlySalary;
        @DecimalMin("0") @DecimalMax("100") private BigDecimal commissionRate;
        @Min(0) private Long targetCoins;
        @Min(0) private Long agencyTargetCoins;
    }

    /** Admin panel: change the numbers (all optional). */
    @Getter
    @Setter
    public static class Update {
        @DecimalMin("0") private BigDecimal monthlySalary;
        @DecimalMin("0") @DecimalMax("100") private BigDecimal commissionRate;
        @Min(0) private Long targetCoins;
        @Min(0) private Long agencyTargetCoins;
    }

    /** App: give a store item. userRef = the person's short ID or internal id. */
    @Getter
    @Setter
    public static class GiveWare {
        @NotBlank private String itemId;
        @NotBlank private String userRef;
        @Min(1) @Max(365) private Integer days;
    }

    /** App: give a badge. days empty = permanent. */
    @Getter
    @Setter
    public static class GiveBadge {
        @NotBlank private String badgeId;
        @NotBlank private String userRef;
        @Min(1) @Max(3650) private Integer days;
    }
}
