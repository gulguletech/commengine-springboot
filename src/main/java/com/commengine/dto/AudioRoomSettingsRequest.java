package com.commengine.dto;

import com.commengine.entity.RoomType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class AudioRoomSettingsRequest {
    @NotNull
    private RoomType type;

    @Pattern(regexp = "^\\d{4}$", message = "Passcode must be exactly 4 digits")
    private String passcode;

    private boolean passcodeProvided = false;

    public RoomType getType() { return type; }
    public void setType(RoomType type) { this.type = type; }
    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; this.passcodeProvided = true; }
    public boolean isPasscodeProvided() { return passcodeProvided; }
}
