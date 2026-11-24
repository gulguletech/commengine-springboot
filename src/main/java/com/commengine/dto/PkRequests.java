package com.commengine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public final class PkRequests {

    private PkRequests() {}

    /** userIds in the order picked: 1V1 = exactly 2, MULTI = 3 to 8. */
    @Getter
    @Setter
    public static class Start {
        @NotBlank private String mode;
        @NotNull @Size(min = 2, max = 8) private List<String> userIds;
        @NotNull @Min(1) @Max(60) private Integer minutes;
    }
}
