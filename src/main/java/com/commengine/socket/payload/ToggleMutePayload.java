package com.commengine.socket.payload;

public class ToggleMutePayload {
    private String roomId;
    private Boolean isMuted;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public Boolean getIsMuted() { return isMuted; }
    public void setIsMuted(Boolean isMuted) { this.isMuted = isMuted; }
}
