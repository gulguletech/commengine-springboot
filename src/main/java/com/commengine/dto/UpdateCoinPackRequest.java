package com.commengine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Every field optional: only the ones sent are changed. */
@Getter
@Setter
public class UpdateCoinPackRequest {
    @Min(1) @Max(100_000_000)
    private Long coins;
    @Min(1) @Max(500_000)
    private Long priceInr;
    private Integer sortOrder;
    private Boolean active;
}
