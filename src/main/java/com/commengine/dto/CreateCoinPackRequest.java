package com.commengine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCoinPackRequest {
    @NotNull @Min(1) @Max(100_000_000)
    private Long coins;

    /** Whole rupees. */
    @NotNull @Min(1) @Max(500_000)
    private Long priceInr;

    private Integer sortOrder;
    private Boolean active;
}
