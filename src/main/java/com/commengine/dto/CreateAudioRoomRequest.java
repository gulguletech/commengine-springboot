package com.commengine.dto;

import com.commengine.entity.RoomType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateAudioRoomRequest {
    @Size(min = 2, max = 80)
    private String title;

    private RoomType type = RoomType.PUBLIC;

    private String coverUrl;

    @Pattern(regexp = "^\\d{4}$", message = "Passcode must be exactly 4 digits")
    private String passcode;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public RoomType getType() { return type == null ? RoomType.PUBLIC : type; }
    public void setType(RoomType type) { this.type = type; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; }
}
