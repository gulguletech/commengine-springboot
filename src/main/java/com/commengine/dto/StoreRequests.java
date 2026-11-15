package com.commengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Request bodies for /api/store. */
public final class StoreRequests {

    private StoreRequests() {}

    @Getter
    @Setter
    public static class Buy {
        @NotBlank private String itemId;
        @NotNull private Integer days;
    }

    @Getter
    @Setter
    public static class Send {
        @NotBlank private String itemId;
        @NotNull private Integer days;
        @NotBlank private String receiverId;
    }

    @Getter
    @Setter
    public static class Equip {
        @NotBlank private String itemId;
    }

    @Getter
    @Setter
    public static class Unequip {
        @NotBlank private String category;
    }
}
