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

    /** Optional: change the room's type (FUN, MUSIC, ...). Null = leave as is. */
    private String category;

    /** Optional: change how many seats the room has (4, 8, 10, 12, 15 or 20; only 4 and 8 are free, the rest need VIP). Null = leave as is. */
    private Integer seatCount;

    public Integer getSeatCount() { return seatCount; }
    public void setSeatCount(Integer seatCount) { this.seatCount = seatCount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    /** Store background for the room. Send null to remove it; leave the field out to keep the current one. */
    private String backgroundItemId;
    private boolean backgroundProvided = false;

    public RoomType getType() { return type; }
    public void setType(RoomType type) { this.type = type; }
    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; this.passcodeProvided = true; }
    public boolean isPasscodeProvided() { return passcodeProvided; }
    public String getBackgroundItemId() { return backgroundItemId; }
    public void setBackgroundItemId(String backgroundItemId) { this.backgroundItemId = backgroundItemId; this.backgroundProvided = true; }
    public boolean isBackgroundProvided() { return backgroundProvided; }
}
