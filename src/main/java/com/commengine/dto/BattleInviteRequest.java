package com.commengine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BattleInviteRequest {
    @NotBlank private String userId;
}
