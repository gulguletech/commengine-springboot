package com.commengine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Request bodies for /api/agency and /api/admin/agencies, /api/admin/agency-levels. */
public final class AgencyRequests {

    private AgencyRequests() {}

    /** App: the owner invites a host by their short user ID. */
    @Getter
    @Setter
    public static class Invite {
        @NotBlank private String hostId;
    }

    /** App: a user accepts or declines an invitation. */
    @Getter
    @Setter
    public static class Respond {
        @NotNull private Boolean accept;
    }

    /** Admin: new agency + who owns it. `ownerId` is the user's internal id (picked from the Users list). */
    @Getter
    @Setter
    public static class AdminCreate {
        @NotBlank @Size(min = 2, max = 80) private String name;
        @Size(max = 200) private String welcomeMessage;
        @NotBlank private String ownerId;
    }

    /** Admin: every field optional. */
    @Getter
    @Setter
    public static class AdminUpdate {
        @Size(min = 2, max = 80) private String name;
        @Size(max = 200) private String welcomeMessage;
        private Boolean active;
        private String ownerId;
    }

    @Getter
    @Setter
    public static class AdminAddMember {
        @NotBlank private String userId;
    }

    @Getter
    @Setter
    public static class LevelCreate {
        @NotNull @Min(1) private Integer level;
        @NotNull @Min(0) private Long diamonds;
        @NotNull @DecimalMin("0") private BigDecimal hostSalary;
        @NotNull @DecimalMin("0") private BigDecimal agencySalary;
    }

    @Getter
    @Setter
    public static class LevelUpdate {
        @Min(1) private Integer level;
        @Min(0) private Long diamonds;
        @DecimalMin("0") private BigDecimal hostSalary;
        @DecimalMin("0") private BigDecimal agencySalary;
    }
}
