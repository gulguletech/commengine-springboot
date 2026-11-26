package com.commengine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public final class AgencyRequestRequests {

    private AgencyRequestRequests() {}

    /** The "Apply for agency" form. The three pictures are uploaded first; these are their links. */
    @Getter
    @Setter
    public static class Submit {
        @NotBlank @Size(min = 2, max = 80) private String fullName;
        @NotBlank @Size(min = 2, max = 80) private String agencyName;
        @NotBlank @Size(min = 3, max = 1000) private String experience;
        @NotNull @Min(1) @Max(5000) private Integer expectedHosts;
        @NotBlank private String aadhaarFrontUrl;
        @NotBlank private String aadhaarBackUrl;
        @NotBlank private String selfieUrl;
    }

    /** Admin panel: optionally put the new agency under a super admin. */
    @Getter
    @Setter
    public static class Approve {
        private String superAdminId;
    }

    @Getter
    @Setter
    public static class Reject {
        @Size(max = 300) private String reason;
    }
}
