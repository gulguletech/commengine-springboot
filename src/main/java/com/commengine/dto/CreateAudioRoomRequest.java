package com.commengine.dto;

import com.commengine.entity.RoomType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateAudioRoomRequest {
    @Size(min = 2, max = 80)
    private String title;

    private RoomType type = RoomType.PUBLIC;

    private String coverUrl;

    /** FUN, MUSIC, GAME, BIRTHDAY, FRIENDS, PARTY or CHAT. Blank = FUN. */
    private String category;
    /** Store background (free, or paid and owned) to put in the room. Optional. */
    private String backgroundItemId;

    @Pattern(regexp = "^\\d{4}$", message = "Passcode must be exactly 4 digits")
    private String passcode;

    /** 4, 8, 12, 16 or 20 seats. Blank = 12. */
    private Integer seatCount;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public RoomType getType() { return type == null ? RoomType.PUBLIC : type; }
    public void setType(RoomType type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getBackgroundItemId() { return backgroundItemId; }
    public void setBackgroundItemId(String backgroundItemId) { this.backgroundItemId = backgroundItemId; }
    public Integer getSeatCount() { return seatCount; }
    public void setSeatCount(Integer seatCount) { this.seatCount = seatCount; }
    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; }
}
