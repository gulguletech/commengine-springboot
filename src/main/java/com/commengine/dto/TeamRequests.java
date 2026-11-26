package com.commengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public final class TeamRequests {

    private TeamRequests() {}

    /** Invite one person (as a BD, or as a direct host) by their short user ID. */
    @Getter
    @Setter
    public static class InviteTarget {
        @NotBlank private String userRef;
    }

    /** The "Invite Agency" form. Owner and hosts are short user IDs. */
    @Getter
    @Setter
    public static class InviteAgency {
        @NotBlank private String ownerId;
        @NotBlank @Size(min = 2, max = 80) private String name;
        @NotBlank @Size(min = 6, max = 30) private String whatsapp;
        @NotBlank @Size(max = 60) private String country;
        private String logoUrl;
        private String idProofUrl;
        @Size(max = 5) private List<String> hostIds;
    }

    @Getter
    @Setter
    public static class Respond {
        private boolean accept;
    }

    // ---- admin panel: BD commission ladder
    @Getter
    @Setter
    public static class TierCreate {
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") private java.math.BigDecimal minSalary;
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") private java.math.BigDecimal commission;
    }

    @Getter
    @Setter
    public static class TierUpdate {
        @jakarta.validation.constraints.DecimalMin("0") private java.math.BigDecimal minSalary;
        @jakarta.validation.constraints.DecimalMin("0") private java.math.BigDecimal commission;
    }
}
