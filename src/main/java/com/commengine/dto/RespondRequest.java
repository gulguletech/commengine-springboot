package com.commengine.dto;

import jakarta.validation.constraints.NotNull;

/** Reused for both audio join-request responses and friend-request responses: { accept: boolean }. */
public class RespondRequest {
    @NotNull(message = "accept (boolean) is required")
    private Boolean accept;

    public Boolean getAccept() { return accept; }
    public void setAccept(Boolean accept) { this.accept = accept; }
}
