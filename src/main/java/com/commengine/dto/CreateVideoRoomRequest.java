package com.commengine.dto;

import jakarta.validation.constraints.Size;

public class CreateVideoRoomRequest {
    @Size(min = 2, max = 80)
    private String title;

    private String coverUrl;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
}
