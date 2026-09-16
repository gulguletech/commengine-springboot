package com.commengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyWidgetTokenRequest(
        @NotBlank(message = "accessToken is required") @Size(min = 10, message = "accessToken is required") String accessToken
) {}
